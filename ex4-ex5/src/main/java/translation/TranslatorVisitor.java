package translation;

import minillvm.ast.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.Optional;
import java.util.stream.Collectors;

import minijava.ast.*;

public class TranslatorVisitor extends MJElement.DefaultVisitor {

    Prog finalProg;
    Proc mainProcedure;

    Map<String, Variable> scopeVars = new LinkedHashMap<>();

    Map<MJVarDecl, Integer> classFieldsIndexes = new HashMap<>();
    Map<String, Integer> classMethodIndexes = new HashMap<>();

    Map<MJClassDecl, Proc> classesConstructors = new HashMap<>();
    Map<String, TypeStruct> classesStructs = new HashMap<>();
    Map<MJClassDecl, TypeStruct> classesvTableStructs = new HashMap<>();
    Map<MJClassDecl, Global> classesvTableGlobal = new HashMap<>();

    Map<MJMethodDecl, Proc> methodsProcs = new HashMap<>();
    List<MJClassDecl> alreadyGeneratedMethodProcs = new ArrayList<>();

    BasicBlockList currentBasicBlockList;
    Proc currentProc;

    // info from analysis process set through setter
    Map<MJElement, MJElement> typeAnalysisInfo;

    MJClassDeclList classDeclList;

    private class Result<T, K> {
        T first;
        K second;

        public Result(T f, K s) {
            this.first = f;
            this.second = s;
        }
    }

    @Override
    public void visit(MJProgram program) {
        this.finalProg = Ast.Prog(Ast.TypeStructList(), Ast.GlobalList(), Ast.ProcList());
        this.classDeclList = program.getClassDecls();

        // init type structures for all classes (before adding fields and methods,
        // because of possible cyclic dependance)
        this.classDeclList.forEach(tcd -> {
            if (!this.classesStructs.containsKey(tcd.getName())) {
                TypeStruct classTypeStruct = Ast.TypeStruct(tcd.getName(), Ast.StructFieldList());
                // creating the constructor
                // BasicBlock constBody = Ast.BasicBlock();
                Proc classConstructor = Ast.Proc(tcd.getName() + "-default-constructor",
                        Ast.TypePointer(classTypeStruct), Ast.ParameterList(/* thisParameter */), Ast.BasicBlockList());
                // vtable struct
                StructFieldList classvTableFields = Ast.StructFieldList();
                TypeStruct classvTableStruct = Ast.TypeStruct(tcd.getName() + "-vtable", classvTableFields);
                // creating global
                ConstList globalvtableConstList = Ast.ConstList();
                Global globalvtable = Ast.Global(classvTableStruct, tcd.getName() + "-vtable-data", true,
                        Ast.ConstStruct(classvTableStruct, globalvtableConstList));

                classesStructs.put(tcd.getName(), classTypeStruct);
                classesConstructors.put(tcd, classConstructor);
                classesvTableGlobal.put(tcd, globalvtable);
                classesvTableStructs.put(tcd, classvTableStruct);
                finalProg.getStructTypes().add(classvTableStruct);
                finalProg.getStructTypes().add(classTypeStruct);
                finalProg.getProcedures().add(classConstructor);
                finalProg.getGlobals().add(globalvtable);

            }
        });
        this.classDeclList.forEach(cd -> generateClassStructs(cd));
        this.classDeclList.forEach(cd -> generateMethodsProc(cd));
        this.classDeclList.forEach(cd -> generateMethodsBodies(cd));

        program.getMainClass().accept(this);
        // program.getClassDecls().accept(this);
    }

    @Override
    public void visit(MJMainClass mainClass) {
        this.mainProcedure = Ast.Proc("main", Ast.TypeInt(), Ast.ParameterList(), Ast.BasicBlockList());
        this.finalProg.getProcedures().add(this.mainProcedure);
        this.currentBasicBlockList = this.mainProcedure.getBasicBlocks();
        mainClass.getMainBody().accept(this);
        this.currentBasicBlockList.get(this.currentBasicBlockList.size() - 1).add(Ast.ReturnExpr(Ast.ConstInt(0)));
    }

    @Override
    public void visit(MJBlock block) {
        BasicBlock lastBlock;
        if (this.currentBasicBlockList.isEmpty()) {
            lastBlock = Ast.BasicBlock();
            this.currentBasicBlockList.add(lastBlock);
        } else
            lastBlock = this.currentBasicBlockList.get(this.currentBasicBlockList.size() - 1);
        for (MJStatement i : block) {
            lastBlock = generateBasicBlock(i, lastBlock);
        }
        // lastBlock.add(Ast.ReturnExpr(Ast.ConstInt(0)));
    }

    /**
     * Generates a VarRef given an expression of type ExprL and the current block
     * 
     * @param exprL        an address expression
     * @param contextBlock the current block in which instructions should be added
     * @return returns the VarRef and the last block (contextblock may have been
     *         terminated with a terminating instruction and so we need to return
     *         the current block)
     */
    private Result<VarRef, BasicBlock> generateVarRef(MJExprL exprL, BasicBlock contextBlock) {
        return exprL.match(new MJExprL.Matcher<Result<VarRef, BasicBlock>>() {

            @Override
            public Result<VarRef, BasicBlock> case_ArrayLookup(MJArrayLookup arrayLookup) {
                contextBlock.add(Ast.CommentInstr("Array Lookup Statement: " + arrayLookup.toString()));
                Result<Operand, BasicBlock> arrayExp = generateOperand(arrayLookup.getArrayExpr(), contextBlock);
                Result<Operand, BasicBlock> index = generateOperand(arrayLookup.getArrayIndex(), arrayExp.second);

                Operand arp = arrayExp.first;
                // checking if null
                BasicBlock lastBlock = checkAndThrowNullPointerException(arp, index.second); // index.second;//
                TemporaryVar lengthValue = Ast.TemporaryVar("lengthValue");
                TemporaryVar t1 = Ast.TemporaryVar("t1");
                lastBlock.add(Ast.Bitcast(t1, Ast.TypePointer(Ast.TypeInt()), arp.copy()));
                lastBlock.add(Ast.Load(lengthValue, Ast.VarRef(t1)));

                TemporaryVar isGreater = Ast.TemporaryVar("IndexGreaterThanZero");
                lastBlock.add(Ast.BinaryOperation(isGreater, Ast.ConstInt(0), Ast.Slt(), index.first));
                TemporaryVar isEqual = Ast.TemporaryVar("IndexEqualToZero");
                lastBlock.add(Ast.BinaryOperation(isEqual, Ast.ConstInt(0), Ast.Eq(), index.first.copy()));
                TemporaryVar isLess = Ast.TemporaryVar("isLessThanLength");
                lastBlock.add(Ast.BinaryOperation(isLess, index.first.copy(), Ast.Slt(), Ast.VarRef(lengthValue)));
                TemporaryVar isGreaterOrEqual = Ast.TemporaryVar("isGreaterOrEqual");
                lastBlock.add(
                        Ast.BinaryOperation(isGreaterOrEqual, Ast.VarRef(isGreater), Ast.Or(), Ast.VarRef(isEqual)));

                BasicBlock validIndex = Ast.BasicBlock();
                BasicBlock outOfBoundsBranch = Ast.BasicBlock(Ast.HaltWithError("OutOfBounds Exception!"));
                currentBasicBlockList.add(outOfBoundsBranch);
                currentBasicBlockList.add(validIndex);
                TemporaryVar isIndexValid = Ast.TemporaryVar("isIndexValid");
                lastBlock.add(
                        Ast.BinaryOperation(isIndexValid, Ast.VarRef(isLess), Ast.And(), Ast.VarRef(isGreaterOrEqual)));
                lastBlock.add(Ast.Branch(Ast.VarRef(isIndexValid), validIndex, outOfBoundsBranch));

                // valid index, lookup the element
                TemporaryVar arAddress = Ast.TemporaryVar("araddress");
                TemporaryVar casted = Ast.TemporaryVar("t");
                validIndex.add(Ast.Bitcast(casted, Ast.TypePointer(Ast.TypeInt()), arp));
                // skip the size of the array
                validIndex.add(Ast.GetElementPtr(arAddress, Ast.VarRef(casted), Ast.OperandList(Ast.ConstInt(1))));

                TemporaryVar dataAddress = Ast.TemporaryVar("dataAddress");
                TemporaryVar baseAddress = Ast.TemporaryVar("baseAddress");
                // cast back
                validIndex.add(Ast.Bitcast(baseAddress, arp.calculateType(), Ast.VarRef(arAddress)));

                validIndex.add(
                        Ast.GetElementPtr(dataAddress, Ast.VarRef(baseAddress), Ast.OperandList(index.first.copy())));
                // validIndex.add(Ast.Load(itemValue, Ast.VarRef(arrayAddress)));

                return new Result<VarRef, BasicBlock>(Ast.VarRef(dataAddress), validIndex);
            }

            @Override
            public Result<VarRef, BasicBlock> case_VarUse(MJVarUse varUse) {
                contextBlock.add(Ast.CommentInstr("Var Use Statement: " + varUse.toString()));

                // TemporaryVar res = Ast.TemporaryVar("res");
                // contextBlock.add(Ast.Load(res, Ast.VarRef()));
                Variable resVar = scopeVars.get(varUse.getVarName());
                if (!scopeVars.containsKey(varUse.getVarName())) {
                    // getting from fields
                    int fieldIndex = classFieldsIndexes.get(typeAnalysisInfo.get(varUse)); // idx
                    TemporaryVar fieldValue = Ast.TemporaryVar("fv");

                    contextBlock.add(Ast.GetElementPtr(fieldValue, Ast.VarRef(scopeVars.get("this")),
                            Ast.OperandList(Ast.ConstInt(0), Ast.ConstInt(1 + fieldIndex))));
                    resVar = fieldValue;
                }
                return new Result<VarRef, BasicBlock>(Ast.VarRef(resVar), contextBlock);
            }

            @Override
            public Result<VarRef, BasicBlock> case_FieldAccess(MJFieldAccess fieldAccess) {
                contextBlock.add(Ast.CommentInstr("FieldAcces Statement: " + fieldAccess.toString()));
                Result<Operand, BasicBlock> receiverRes = generateOperand(fieldAccess.getReceiver(), contextBlock);
                BasicBlock lastBlock = receiverRes.second;

                // get Vardecl of field
                lastBlock = checkAndThrowNullPointerException(receiverRes.first, lastBlock);
                TemporaryVar fieldValue = Ast.TemporaryVar("fieldValue");
                lastBlock.add(Ast.GetElementPtr(fieldValue, receiverRes.first, Ast.OperandList(Ast.ConstInt(0),
                        Ast.ConstInt(classFieldsIndexes.get(typeAnalysisInfo.get(fieldAccess)) + 1))));

                return new Result<VarRef, BasicBlock>(Ast.VarRef(fieldValue), lastBlock);
            }

        });
    }

    /**
     * Generates an Operand given an expression and the current block
     * 
     * @param expr         expression to translate
     * @param contextBlock current block in which to write eventual instructions
     * @return returns the operand and the last block (contextblock may have been
     *         terminated with a terminating instruction and so we need to return
     *         the current block)
     */
    private Result<Operand, BasicBlock> generateOperand(MJExpr expr, BasicBlock contextBlock) {
        return expr.match(new MJExpr.Matcher<Result<Operand, BasicBlock>>() {

            @Override
            public Result<Operand, BasicBlock> case_Number(MJNumber number) {
                return new Result<Operand, BasicBlock>(Ast.ConstInt(number.getIntValue()), contextBlock);
            }

            @Override
            public Result<Operand, BasicBlock> case_ExprUnary(MJExprUnary exprUnary) {
                Result<Operand, BasicBlock> exprRes = generateOperand(exprUnary.getExpr(), contextBlock);
                return new Result<Operand, BasicBlock>(
                        generateUnaryOperator(exprUnary.getUnaryOperator(), exprRes.first, exprRes.second),
                        exprRes.second);
            }

            @Override
            public Result<Operand, BasicBlock> case_NewObject(MJNewObject newObject) {
                Proc classConstructor = classesConstructors.get(typeAnalysisInfo.get(newObject));
                TemporaryVar objp = Ast.TemporaryVar("objp");
                contextBlock.add(Ast.Call(objp, Ast.ProcedureRef(classConstructor), Ast.OperandList()));
                return new Result<Operand, BasicBlock>(Ast.VarRef(objp), contextBlock);
            }

            @Override
            public Result<Operand, BasicBlock> case_MethodCall(MJMethodCall methodCall) {
                contextBlock.add(Ast.CommentInstr("MethodCall Statement: " + methodCall.toString()));

                Result<Operand, BasicBlock> receiverRes = generateOperand(methodCall.getReceiver(), contextBlock);
                BasicBlock lastBlock = receiverRes.second;
                // get the order of the method
                MJMethodDecl md = (MJMethodDecl) typeAnalysisInfo.get(methodCall);

                lastBlock = checkAndThrowNullPointerException(receiverRes.first, lastBlock);

                TypeStruct typeReceiver = ((TypeStruct) ((TypePointer) receiverRes.first.calculateType()).getTo());

                int methodIndex = classMethodIndexes.get(typeReceiver.getName() + "-" + md.getName());
                // ((MJClassDecl) md.getParent().getParent()).getMethods().indexOf(md);
                TemporaryVar vtablePointer = Ast.TemporaryVar("vtablePointer");
                TemporaryVar tp = Ast.TemporaryVar("tp");
                TemporaryVar tp2 = Ast.TemporaryVar("tp2");
                TemporaryVar functionPointer = Ast.TemporaryVar(md.getName());
                TemporaryVar callReturn = Ast.TemporaryVar("callReturn");
                lastBlock.add(
                        Ast.GetElementPtr(tp, receiverRes.first, Ast.OperandList(Ast.ConstInt(0), Ast.ConstInt(0))));
                lastBlock.add(Ast.Load(vtablePointer, Ast.VarRef(tp)));
                lastBlock.add(Ast.GetElementPtr(tp2, Ast.VarRef(vtablePointer),
                        Ast.OperandList(Ast.ConstInt(0), Ast.ConstInt(methodIndex))));
                lastBlock.add(Ast.Load(functionPointer, Ast.VarRef(tp2)));

                TypeProc functionType = (TypeProc) ((TypePointer) functionPointer.calculateType()).getTo();

                Type objType = functionType.getArgTypes().get(0);
                Operand thisObj = receiverRes.first.copy();
                if (!objType.equalsType(thisObj.calculateType())) {
                    TemporaryVar castedObj = Ast.TemporaryVar("obj");
                    lastBlock.add(Ast.Bitcast(castedObj, objType, receiverRes.first.copy()));
                    thisObj = Ast.VarRef(castedObj);
                }
                OperandList argumentsOps = Ast.OperandList();
                argumentsOps.add(thisObj);

                MJExprList mdArguments = methodCall.getArguments();
                for (int i = 0; i < mdArguments.size(); i++) {
                    MJExpr arg = mdArguments.get(i);
                    Result<Operand, BasicBlock> argOpRes = generateOperand(arg, lastBlock);
                    lastBlock = argOpRes.second;

                    Operand resArg = argOpRes.first.copy();

                    Type expectedType = functionType.getArgTypes().get(i + 1);
                    Type givenType = argOpRes.first.calculateType();
                    if (!givenType.equalsType(expectedType)) {
                        TemporaryVar castedArg = Ast.TemporaryVar("castedArg");
                        lastBlock.add(Ast.Bitcast(castedArg, expectedType, resArg));

                        Type castedType = castedArg.calculateType();
                        resArg = Ast.VarRef(castedArg);
                    }
                    argumentsOps.add(resArg);
                }
                lastBlock.add(Ast.Call(callReturn, Ast.VarRef(functionPointer), argumentsOps));
                return new Result<Operand, BasicBlock>(Ast.VarRef(callReturn), lastBlock);
            }

            @Override
            public Result<Operand, BasicBlock> case_Read(MJRead read) {
                Result<VarRef, BasicBlock> res = generateVarRef(read.getAddress(), contextBlock);
                if (res.first.getVariable() instanceof Parameter)
                    return new Result<Operand, BasicBlock>(res.first, res.second);

                TemporaryVar t = Ast.TemporaryVar(res.first.getVariable().getName());
                res.second.add(Ast.Load(t, res.first));
                return new Result<Operand, BasicBlock>(Ast.VarRef(t), res.second);
            }

            @Override
            public Result<Operand, BasicBlock> case_BoolConst(MJBoolConst boolConst) {
                return new Result<Operand, BasicBlock>(Ast.ConstBool(boolConst.getBoolValue()), contextBlock);
            }

            @Override
            public Result<Operand, BasicBlock> case_ExprNull(MJExprNull exprNull) {
                return new Result<Operand, BasicBlock>(Ast.Nullpointer(), contextBlock);
            }

            @Override
            public Result<Operand, BasicBlock> case_ArrayLength(MJArrayLength arrayLength) {
                // contextBlock.add(Ast.CommentInstr("Array Length Statement"));
                Result<Operand, BasicBlock> arrayExp = generateOperand(arrayLength.getArrayExpr(), contextBlock);

                VarRef arp = (VarRef) arrayExp.first;

                // checking if null
                BasicBlock lastBlock = checkAndThrowNullPointerException(arp, arrayExp.second);
                TemporaryVar lengthValue = Ast.TemporaryVar("lengthValue");
                TemporaryVar t1 = Ast.TemporaryVar("t1");
                lastBlock.add(Ast.Bitcast(t1, Ast.TypePointer(Ast.TypeInt()), arp.copy()));
                lastBlock.add(Ast.Load(lengthValue, Ast.VarRef(t1)));
                return new Result<Operand, BasicBlock>(Ast.VarRef(lengthValue), lastBlock);
            }

            @Override
            public Result<Operand, BasicBlock> case_ExprThis(MJExprThis exprThis) {
                return new Result<Operand, BasicBlock>(Ast.VarRef(scopeVars.get("this")), contextBlock);
            }

            @Override
            public Result<Operand, BasicBlock> case_ExprBinary(MJExprBinary exprBinary) {
                // contextBlock.add(Ast.CommentInstr("Binary Expr Statement"));

                Result<Operand, BasicBlock> leftRes = generateOperand(exprBinary.getLeft(), contextBlock);
                Result<Operand, BasicBlock> rightRes;
                BasicBlock lastBlock = leftRes.second;

                Operator operator = generateOperator(exprBinary.getOperator());

                TemporaryVar result = Ast.TemporaryVar("result");
                BasicBlock divByZero = Ast.BasicBlock();
                divByZero.setName("divByZero");
                BasicBlock divValid = Ast.BasicBlock();
                divValid.setName("divValid");

                if (operator instanceof And) {
                    // evaluate only left
                    TemporaryVar t1 = Ast.TemporaryVar("resAndLeft");
                    TemporaryVar t2 = t1.copy();

                    BasicBlock isLeftAndFalse = Ast.BasicBlock();
                    BasicBlock isLeftAndTrue = Ast.BasicBlock();
                    BasicBlock afterAnd = Ast.BasicBlock();

                    lastBlock.add(Ast.Alloca(result, Ast.TypeBool()));
                    lastBlock.add(Ast.BinaryOperation(t1, leftRes.first, Ast.Eq(), Ast.ConstBool(true)));
                    lastBlock.add(Ast.Branch(Ast.VarRef(t1), isLeftAndTrue, isLeftAndFalse));

                    rightRes = generateOperand(exprBinary.getRight(), isLeftAndTrue);
                    lastBlock = rightRes.second;
                    isLeftAndFalse.add(Ast.Store(Ast.VarRef(result), Ast.ConstBool(false)));
                    isLeftAndFalse.add(Ast.Jump(afterAnd));

                    lastBlock.add(Ast.Store(Ast.VarRef(result), rightRes.first));
                    lastBlock.add(Ast.Jump(afterAnd));

                    afterAnd.add(Ast.Load(t2, Ast.VarRef(result)));
                    currentBasicBlockList.add(isLeftAndFalse);
                    currentBasicBlockList.add(isLeftAndTrue);
                    currentBasicBlockList.add(afterAnd);
                    return new Result<Operand, BasicBlock>(Ast.VarRef(t2), afterAnd);
                }
                rightRes = generateOperand(exprBinary.getRight(), lastBlock);
                lastBlock = rightRes.second;
                //
                // if the operator is Sdiv we have to consider about the possibility of divide
                // by zero
                if (operator instanceof Sdiv) {
                    TemporaryVar isZero = Ast.TemporaryVar("isZero");
                    BinaryOperation condition = Ast.BinaryOperation(isZero, rightRes.first, Ast.Eq(), Ast.ConstInt(0));
                    lastBlock.add(condition);

                    Branch divBranch = Ast.Branch(Ast.VarRef(isZero), divByZero, divValid);
                    lastBlock.add(divBranch);

                    currentBasicBlockList.add(divByZero);
                    divByZero.add(Ast.HaltWithError("Division by zero"));

                    currentBasicBlockList.add(divValid);
                    lastBlock = divValid;

                    // check for possible overflow
                    TemporaryVar n1 = Ast.TemporaryVar("n1");
                    TemporaryVar n2 = Ast.TemporaryVar("n2");
                    TemporaryVar n1andn2 = Ast.TemporaryVar("n1andn2");
                    lastBlock.add(Ast.BinaryOperation(n1, leftRes.first, Ast.Eq(), Ast.ConstInt(-2147483648)));
                    lastBlock.add(Ast.BinaryOperation(n2, rightRes.first.copy(), Ast.Eq(), Ast.ConstInt(-1)));
                    lastBlock.add(Ast.BinaryOperation(n1andn2, Ast.VarRef(n1), Ast.And(), Ast.VarRef(n2)));

                    TemporaryVar divRes = Ast.TemporaryVar("divRes");
                    lastBlock.add(Ast.Alloca(divRes, Ast.TypeInt()));

                    BasicBlock afterOverflowCheck = Ast.BasicBlock(Ast.Load(result, Ast.VarRef(divRes)));

                    TemporaryVar t = Ast.TemporaryVar("t");
                    BasicBlock isOverflow = Ast.BasicBlock(
                            // Ast.BinaryOperation(t, Ast.ConstInt(-2147483648), operator,
                            // rightRes.first.copy()),
                            Ast.Store(Ast.VarRef(divRes), Ast.ConstInt(-2147483648)), Ast.Jump(afterOverflowCheck));

                    TemporaryVar t2 = Ast.TemporaryVar("t2");
                    BasicBlock nonOverflow = Ast.BasicBlock(
                            Ast.BinaryOperation(t2, leftRes.first.copy(), operator.copy(), rightRes.first.copy()),
                            Ast.Store(Ast.VarRef(divRes), Ast.VarRef(t2)), Ast.Jump(afterOverflowCheck));

                    lastBlock.add(Ast.Branch(Ast.VarRef(n1andn2), isOverflow, nonOverflow));

                    currentBasicBlockList.add(isOverflow);
                    currentBasicBlockList.add(nonOverflow);
                    currentBasicBlockList.add(afterOverflowCheck);
                    lastBlock = afterOverflowCheck;
                } else {
                    Operand right = rightRes.first;
                    Operand left = leftRes.first;

                    Type leftType = leftRes.first.calculateType();
                    Type rightType = rightRes.first.calculateType();
                    if (!leftType.equalsType(rightType)) {
                        // need to cast
                        TemporaryVar castedRight = Ast.TemporaryVar("castedOperand");
                        lastBlock.add(Ast.Bitcast(castedRight, leftType, right));
                        right = Ast.VarRef(castedRight);
                    }

                    lastBlock.add(Ast.BinaryOperation(result, leftRes.first, operator, right));
                }

                return new Result<Operand, BasicBlock>(Ast.VarRef(result), lastBlock);
            }

            @Override
            public Result<Operand, BasicBlock> case_NewArray(MJNewArray newArray) {
                // contextBlock.add(Ast.CommentInstr("New Array Statement: " +
                // newArray.toString()));

                Result<Operand, BasicBlock> sizeOfArrayRes = generateOperand(newArray.getArraySize(), contextBlock);
                Operand sizeOfArray = sizeOfArrayRes.first;
                BasicBlock lastBlock = sizeOfArrayRes.second;

                TemporaryVar isGreater = Ast.TemporaryVar("LengthGreaterThanZero");
                lastBlock.add(Ast.BinaryOperation(isGreater, Ast.ConstInt(0), Ast.Slt(), sizeOfArray));
                TemporaryVar isEqual = Ast.TemporaryVar("LengthEqualToZero");
                lastBlock.add(Ast.BinaryOperation(isEqual, Ast.ConstInt(0), Ast.Eq(), sizeOfArray.copy()));
                TemporaryVar isGreaterOrEqual = Ast.TemporaryVar("isGreaterOrEqual");
                lastBlock.add(
                        Ast.BinaryOperation(isGreaterOrEqual, Ast.VarRef(isGreater), Ast.Or(), Ast.VarRef(isEqual)));

                BasicBlock invalidIndex = Ast
                        .BasicBlock(Ast.HaltWithError("The array is defined with invalid length!"));
                invalidIndex.setName("invalidIndexArray");
                BasicBlock validIndex = Ast.BasicBlock();
                validIndex.setName("indexValidArray");
                currentBasicBlockList.add(invalidIndex);
                currentBasicBlockList.add(validIndex);

                lastBlock.add(Ast.Branch(Ast.VarRef(isGreaterOrEqual), validIndex, invalidIndex));

                // allocate the array
                TemporaryVar t1 = Ast.TemporaryVar("t1");
                TemporaryVar t2 = Ast.TemporaryVar("t2sizearray"); // inbytes
                validIndex.add(Ast.BinaryOperation(t1, sizeOfArray.copy(), Ast.Mul(),
                        newArray.getBaseType().match(new MJType.Matcher<Operand>() {

                            @Override
                            public Operand case_TypeInt(MJTypeInt typeInt) {
                                return Ast.ConstInt(4);
                            }

                            @Override
                            public Operand case_TypeBool(MJTypeBool typeBool) {
                                return Ast.ConstInt(1);
                            }

                            @Override
                            public Operand case_TypeClass(MJTypeClass typeClass) {
                                return Ast.Sizeof(classesStructs.get(typeClass.getName()));
                            }

                            @Override
                            public Operand case_TypeArray(MJTypeArray typeArray) {

                                return Ast.ConstInt(4);
                            }
                        })));

                validIndex.add(Ast.BinaryOperation(t2, Ast.VarRef(t1), Ast.Add(), Ast.ConstInt(4)));
                Type pointedType = generateType(newArray.getBaseType());

                TemporaryVar tmpAddress = Ast.TemporaryVar("tmpaddress");
                TemporaryVar tmpAddress2 = Ast.TemporaryVar("tmpaddress2");
                TemporaryVar addressArray = Ast.TemporaryVar("arrayAddress");
                validIndex.add(Ast.Alloc(tmpAddress, Ast.VarRef(t2)));
                validIndex.add(Ast.Bitcast(tmpAddress2, Ast.TypePointer(Ast.TypeInt()), Ast.VarRef(tmpAddress)));
                validIndex.add(Ast.Store(Ast.VarRef(tmpAddress2), sizeOfArray.copy()));

                validIndex.add(Ast.Bitcast(addressArray, Ast.TypePointer(pointedType), Ast.VarRef(tmpAddress2)));

                // adding while for initializing the array to zero in case of intarray (test
                // arrayallocstore fails otherwise)
                if (!(pointedType instanceof TypeBool)) {
                    BasicBlock conditionBlock = Ast.BasicBlock();
                    BasicBlock trueBlock = Ast.BasicBlock();
                    trueBlock.setName("conditionTrue");
                    BasicBlock falseBlock = Ast.BasicBlock();
                    falseBlock.setName("conditionFalse");

                    TemporaryVar idx = Ast.TemporaryVar("idx");
                    TemporaryVar condition = Ast.TemporaryVar("condition");
                    TemporaryVar incSize = Ast.TemporaryVar("incSize");

                    validIndex.add(Ast.Alloca(idx, Ast.TypeInt()));
                    validIndex.add(Ast.Store(Ast.VarRef(idx), Ast.ConstInt(1)));
                    validIndex.add(Ast.BinaryOperation(incSize, sizeOfArray.copy(), Ast.Add(), Ast.ConstInt(1)));
                    validIndex.add(Ast.Jump(conditionBlock)); // close contextBlock

                    TemporaryVar lidx = Ast.TemporaryVar("lidx");
                    conditionBlock.add(Ast.Load(lidx, Ast.VarRef(idx)));
                    conditionBlock
                            .add(Ast.BinaryOperation(condition, Ast.VarRef(lidx), Ast.Slt(), Ast.VarRef(incSize)));
                    conditionBlock.add(Ast.Branch(Ast.VarRef(condition), trueBlock, falseBlock));

                    TemporaryVar itemAddress = Ast.TemporaryVar("itemAddress");
                    trueBlock.add(Ast.GetElementPtr(itemAddress, Ast.VarRef(addressArray),
                            Ast.OperandList(Ast.VarRef(lidx))));

                    trueBlock.add(Ast.Store(Ast.VarRef(itemAddress),
                            pointedType instanceof TypeInt ? Ast.ConstInt(0) : Ast.Nullpointer()));

                    // increment while idx
                    TemporaryVar oidx = Ast.TemporaryVar("oidx");
                    TemporaryVar incidx = Ast.TemporaryVar("incIdx");
                    trueBlock.add(Ast.Load(oidx, Ast.VarRef(idx)));
                    trueBlock.add(Ast.BinaryOperation(incidx, Ast.VarRef(oidx), Ast.Add(), Ast.ConstInt(1)));
                    trueBlock.add(Ast.Store(Ast.VarRef(idx), Ast.VarRef(incidx)));

                    trueBlock.add(Ast.Jump(conditionBlock));

                    currentBasicBlockList.add(conditionBlock);
                    currentBasicBlockList.add(trueBlock);
                    currentBasicBlockList.add(falseBlock);

                    return new Result<Operand, BasicBlock>(Ast.VarRef(addressArray), falseBlock);
                }

                return new Result<Operand, BasicBlock>(Ast.VarRef(addressArray), validIndex);

            }
        });
    }

    /**
     * Generates instructions and adds them to the given contextblock
     * 
     * @param statement    the statement to translate
     * @param contextBlock the current block in which to add the eventual
     *                     instructions
     * @return the last added basicblock, since contextBlock may have been
     *         terminated and other blocks added
     */
    private BasicBlock generateBasicBlock(MJStatement statement, BasicBlock contextBlock) {
        return statement.match(new MJStatement.Matcher<BasicBlock>() {

            @Override
            public BasicBlock case_StmtWhile(MJStmtWhile stmtWhile) {
                contextBlock.add(Ast.CommentInstr("While statement: " + stmtWhile.toString()));
                BasicBlock conditionBlock = Ast.BasicBlock();
                BasicBlock trueBlock = Ast.BasicBlock();
                trueBlock.setName("conditionTrue");
                BasicBlock falseBlock = Ast.BasicBlock();
                falseBlock.setName("conditionFalse");

                currentBasicBlockList.add(conditionBlock);
                contextBlock.add(Ast.Jump(conditionBlock)); // close contextBlock

                Result<Operand, BasicBlock> conditionOperandRes = generateOperand(stmtWhile.getCondition(),
                        conditionBlock);
                Branch whileBranch = Ast.Branch(conditionOperandRes.first, trueBlock, falseBlock);
                conditionOperandRes.second.add(whileBranch);

                currentBasicBlockList.add(trueBlock);
                BasicBlock lastBlock = generateBasicBlock(stmtWhile.getLoopBody(), trueBlock);
                if (lastBlock.isEmpty() || !(lastBlock.get(lastBlock.size() - 1) instanceof TerminatingInstruction))
                    lastBlock.add(Ast.Jump(conditionBlock));

                currentBasicBlockList.add(falseBlock);

                return falseBlock;
            }

            @Override
            public BasicBlock case_StmtExpr(MJStmtExpr stmtExpr) {
                Result<Operand, BasicBlock> exprOp = generateOperand(stmtExpr.getExpr(), contextBlock);
                return exprOp.second;
            }

            @Override
            public BasicBlock case_VarDecl(MJVarDecl varDecl) {
                contextBlock.add(Ast.CommentInstr("VarDecl statement: " + varDecl.toString()));

                TemporaryVar tmpVar = Ast.TemporaryVar(varDecl.getName());
                currentBasicBlockList.get(0).addFront(Ast.Alloca(tmpVar, generateType(varDecl.getType())));
                initializeVariable(Ast.VarRef(tmpVar), false, contextBlock);
                // contextBlock.add();
                scopeVars.put(varDecl.getName(), tmpVar);

                return contextBlock;
            }

            @Override
            public BasicBlock case_StmtAssign(MJStmtAssign stmtAssign) {
                contextBlock.add(Ast.CommentInstr("Assign statement: " + stmtAssign.toString()));
                Result<Operand, BasicBlock> rightOpRes = generateOperand(stmtAssign.getRight(), contextBlock);
                BasicBlock lastBlock = rightOpRes.second;

                Result<VarRef, BasicBlock> refLeftRes = generateVarRef(stmtAssign.getAddress(), lastBlock);
                lastBlock = refLeftRes.second;

                Type rightType = rightOpRes.first.calculateType();
                Type leftType = refLeftRes.first.calculateType();
                Operand rightCasted = rightOpRes.first;

                if (rightType instanceof TypePointer) {

                    TemporaryVar res = Ast.TemporaryVar("res");
                    if (leftType.equalsType(rightType)) {
                        lastBlock.add(Ast.Load(res, rightOpRes.first));

                    } else {
                        lastBlock.add(Ast.Bitcast(res, ((TypePointer) refLeftRes.first.calculateType()).getTo(),
                                rightOpRes.first));
                    }
                    rightCasted = Ast.VarRef(res);

                }

                lastBlock.add(Ast.Store(refLeftRes.first, rightCasted));

                return lastBlock;
            }

            @Override
            public BasicBlock case_StmtPrint(MJStmtPrint stmtPrint) {
                contextBlock.add(Ast.CommentInstr("Print statement: " + stmtPrint.toString()));

                Result<Operand, BasicBlock> printedRes = generateOperand(stmtPrint.getPrinted(), contextBlock);
                printedRes.second.add(Ast.Print(printedRes.first));
                return printedRes.second;
            }

            @Override
            public BasicBlock case_StmtIf(MJStmtIf stmtIf) {
                contextBlock.add(Ast.CommentInstr("If statement: " + stmtIf.toString()));
                BasicBlock lastBlock = contextBlock;
                BasicBlock ifCondition = Ast.BasicBlock();
                ifCondition.setName("ifCondition");
                BasicBlock trueBranch = Ast.BasicBlock();
                trueBranch.setName("TrueBranch");
                BasicBlock falseBranch = Ast.BasicBlock();
                falseBranch.setName("FalseBranch");
                BasicBlock afterIf = Ast.BasicBlock();
                afterIf.setName("AfterIf");

                lastBlock.add(Ast.Jump(ifCondition));

                Result<Operand, BasicBlock> conditionRes = generateOperand(stmtIf.getCondition(), ifCondition);
                Branch ifBranch = Ast.Branch(conditionRes.first, trueBranch, falseBranch);
                lastBlock = conditionRes.second;
                lastBlock.add(ifBranch);

                lastBlock = generateBasicBlock(stmtIf.getIfTrue(), trueBranch);
                if (lastBlock.isEmpty() || !(lastBlock.get(lastBlock.size() - 1) instanceof TerminatingInstruction))
                    lastBlock.add(Ast.Jump(afterIf));

                lastBlock = generateBasicBlock(stmtIf.getIfFalse(), falseBranch);
                if (lastBlock.isEmpty() || !(lastBlock.get(lastBlock.size() - 1) instanceof TerminatingInstruction))
                    lastBlock.add(Ast.Jump(afterIf));

                currentBasicBlockList.add(ifCondition);
                currentBasicBlockList.add(trueBranch);
                currentBasicBlockList.add(falseBranch);
                currentBasicBlockList.add(afterIf);

                return afterIf;
            }

            @Override
            public BasicBlock case_Block(MJBlock block) {
                BasicBlock lastBlock = contextBlock;
                for (MJStatement i : block) {
                    lastBlock = generateBasicBlock(i, lastBlock);
                }
                return lastBlock;
            }

            @Override
            public BasicBlock case_StmtReturn(MJStmtReturn stmtReturn) {
                contextBlock.add(Ast.CommentInstr("Return statement: " + stmtReturn.toString()));

                Result<Operand, BasicBlock> valueRes = generateOperand(stmtReturn.getResult(), contextBlock);
                Type methodReturnType = currentProc.getReturnType();
                Type resultType = valueRes.first.calculateType();

                Operand result = valueRes.first;
                if (resultType instanceof TypePointer && !resultType.equalsType(methodReturnType)) {
                    TemporaryVar castedResult = Ast.TemporaryVar("castedResult");
                    valueRes.second.add(Ast.Bitcast(castedResult, methodReturnType, valueRes.first));
                    result = Ast.VarRef(castedResult);
                }

                valueRes.second.add(Ast.ReturnExpr(result));
                return valueRes.second;
            }

        });
    }

    /**
     * Generates an Operand referring to the result of the unary operation
     * 
     * @param operator     the unary operator
     * @param var          the operand
     * @param contextBlock the current block in which to add the eventual
     *                     instructions
     * @return the generated Operand, no basicblock is returned because no
     *         terminating instruction need to be added to the currentblock for the
     *         unary operation
     */
    public Operand generateUnaryOperator(MJUnaryOperator operator, Operand var, BasicBlock contextBlock) {

        TemporaryVar result = Ast.TemporaryVar("res");

        return operator.match(new MJUnaryOperator.Matcher<Operand>() {
            @Override
            public Operand case_UnaryMinus(MJUnaryMinus unaryMinus) {
                contextBlock.add(Ast.BinaryOperation(result, Ast.ConstInt(0), Ast.Sub(), var));
                return Ast.VarRef(result);
            }

            @Override
            public Operand case_Negate(MJNegate negate) {
                contextBlock.add(Ast.BinaryOperation(result, Ast.ConstBool(true), Ast.Xor(), var));
                return Ast.VarRef(result);
            }
        });
    }

    /**
     * Generates branching for checking if an operand is null
     * 
     * @param op           the operand to check
     * @param contextBlock the current block in which to add the eventual
     *                     instructions
     * @return the last added basicblock, since contextBlock may have been
     *         terminated and other blocks added
     */
    private BasicBlock checkAndThrowNullPointerException(Operand op, BasicBlock contextBlock) {
        BasicBlock notNullBranch = Ast.BasicBlock();
        notNullBranch.setName("notNullBranch");

        BasicBlock nullPointerBranch = Ast.BasicBlock(Ast.HaltWithError("Nullpointer Exception"));
        nullPointerBranch.setName("isNullBranch");
        TemporaryVar isNull = Ast.TemporaryVar("isNull");
        contextBlock.add(Ast.BinaryOperation(isNull, op.copy(), Ast.Eq(), Ast.Nullpointer()));
        contextBlock.add(Ast.Branch(Ast.VarRef(isNull), nullPointerBranch, notNullBranch));

        currentBasicBlockList.add(nullPointerBranch);
        currentBasicBlockList.add(notNullBranch);
        return notNullBranch;
    }

    /**
     * Given a type (MJType) generates the corresponding type for LLVM
     * 
     * @param type MJType to translate
     * @return generated type
     */
    private Type generateType(MJType type) {
        return type.match(new MJType.Matcher<Type>() {

            @Override
            public Type case_TypeInt(MJTypeInt typeInt) {
                return Ast.TypeInt();
            }

            @Override
            public Type case_TypeBool(MJTypeBool typeBool) {
                return Ast.TypeBool();
            }

            @Override
            public Type case_TypeClass(MJTypeClass typeClass) {
                return Ast.TypePointer(classesStructs.get(typeClass.getName()));
            }

            @Override
            public Type case_TypeArray(MJTypeArray typeArray) {
                return Ast.TypePointer(generateType(typeArray.getComponentType()));
            }
        });
    }

    /**
     * Generates the corresponding LLVM Operator from an MJOperator
     * 
     * @param op the operator to translate
     * @return generated operator
     */
    private Operator generateOperator(MJOperator op) {
        return op.match(new MJOperator.Matcher<Operator>() {

            @Override
            public Operator case_Plus(MJPlus plus) {
                return Ast.Add();
            }

            @Override
            public Operator case_Minus(MJMinus minus) {
                return Ast.Sub();
            }

            @Override
            public Operator case_Less(MJLess less) {
                return Ast.Slt();
            }

            @Override
            public Operator case_And(MJAnd and) {
                return Ast.And();
            }

            @Override
            public Operator case_Div(MJDiv div) {
                return Ast.Sdiv();
            }

            @Override
            public Operator case_Times(MJTimes times) {
                return Ast.Mul();
            }

            @Override
            public Operator case_Equals(MJEquals equals) {
                return Ast.Eq();
            }
        });

    }

    /**
     * Fills up the type structures of the given class with its fields and
     * initialize them
     * 
     * @param cd class to initialize
     */
    private void generateClassStructs(MJClassDecl cd) {
        // class struct
        TypeStruct classTypeStruct = this.classesStructs.get(cd.getName());
        StructFieldList classTypeStructFields = classTypeStruct.getFields();

        TypeStruct classvTableStruct = this.classesvTableStructs.get(cd);
        Global globalvtable = this.classesvTableGlobal.get(cd);

        BasicBlock constBody = Ast.BasicBlock();
        classesConstructors.get(cd).getBasicBlocks().add(constBody);

        // adding vtable reference to the class struct
        classTypeStructFields.add(Ast.StructField(Ast.TypePointer(classvTableStruct), "vtable"));

        TemporaryVar thisa = Ast.TemporaryVar("thisv");
        TemporaryVar thisc = Ast.TemporaryVar("thisc");
        TemporaryVar thisPointer = Ast.TemporaryVar("thisp");
        constBody.add(Ast.Alloc(thisa, Ast.Sizeof(classTypeStruct)));
        constBody.add(Ast.Bitcast(thisc, Ast.TypePointer(classTypeStruct), Ast.VarRef(thisa)));

        // adding fields considering also inheritance
        recAddFields(cd, classTypeStructFields, constBody);
        // initializing fields
        initializeVariable(Ast.VarRef(thisc), true, constBody);

        // initialize vtable reference
        constBody.add(
                Ast.GetElementPtr(thisPointer, Ast.VarRef(thisc), Ast.OperandList(Ast.ConstInt(0), Ast.ConstInt(0))));
        constBody.add(Ast.Store(Ast.VarRef(thisPointer), Ast.GlobalRef(globalvtable)));

        constBody.add(Ast.ReturnExpr(Ast.VarRef(thisc)));

    }

    /**
     * Translate the bodies of the methods of the given class and fills the
     * corresponding procedures with the generated code
     * 
     * @param cd class to initialize
     */
    private void generateMethodsBodies(MJClassDecl cd) {
        // creating procedure bodies
        cd.getMethods().forEach(md -> {
            // adding parameters to scope before parsing the method body
            Proc correspondingProc = this.methodsProcs.get(md);
            BasicBlock initBlock = correspondingProc.getBasicBlocks().get(0);
            correspondingProc.getParameters().forEach(p -> {

                if (!p.getName().equals("this")) {
                    TemporaryVar allocatedParameter = Ast.TemporaryVar(p.getName() + ".addr");
                    initBlock.add(Ast.Alloca(allocatedParameter, p.getType()));
                    initBlock.add(Ast.Store(Ast.VarRef(allocatedParameter), Ast.VarRef(p)));
                    scopeVars.put(p.getName(), allocatedParameter);
                } else
                    scopeVars.put(p.getName(), p);
            });
            currentBasicBlockList = correspondingProc.getBasicBlocks();
            currentProc = correspondingProc;
            md.getMethodBody().accept(this);
            scopeVars.clear();

        });
    }

    /**
     * Fills up the given structfieldlist with the fields of the given class,
     * considering also its possible supertype
     * 
     * @param cd           interested class
     * @param sfl          structfieldlist to initiliaze
     * @param contextBlock current block in which to add eventual instructions
     */
    private void recAddFields(MJClassDecl cd, StructFieldList sfl, BasicBlock contextBlock) {

        if (cd.getExtended() instanceof MJExtendsClass)
            recAddFields(getClassDeclByName(((MJExtendsClass) cd.getExtended()).getName()), sfl, contextBlock);

        for (int i = 0; i < cd.getFields().size(); i++) {
            MJVarDecl vd = cd.getFields().get(i);
            Type vdType = generateType(vd.getType());

            classFieldsIndexes.put(vd, sfl.size() - 1);
            sfl.add(Ast.StructField(vdType, vd.getName()));
        }
    }

    /**
     * Generates the methods procedures of the given class and adds them to the
     * program
     * 
     * @param cd interested class
     */
    private void generateMethodsProc(MJClassDecl cd) {
        if (alreadyGeneratedMethodProcs.contains(cd))
            return;

        TypeStruct vtableStruct = this.classesvTableStructs.get(cd);
        Global globalvtable = this.classesvTableGlobal.get(cd);

        TypeStruct classTypeStruct = this.classesStructs.get(cd.getName());
        ConstList globalvtableConstList = ((ConstStruct) globalvtable.getInitialValue()).getValues();

        if (cd.getExtended() instanceof MJExtendsClass) {
            MJClassDecl cdExt = getClassDeclByName(((MJExtendsClass) cd.getExtended()).getName());
            generateMethodsProc(cdExt);

            classesvTableStructs.get(cdExt).getFields().forEach(sf -> {
                classMethodIndexes.put(cd.getName() + "-" + sf.getName(), vtableStruct.getFields().size());
                vtableStruct.getFields().add(
                        Ast.StructField(Ast.TypePointer(((TypePointer) sf.getType()).getTo().copy()), sf.getName()));
            });
            ((ConstStruct) classesvTableGlobal.get(cdExt).getInitialValue()).getValues()
                    .forEach(cv -> globalvtableConstList.add(cv.copy()));
        }

        cd.getMethods().forEach(md -> {
            if (!methodsProcs.containsKey(md)) {
                Parameter thisParameter = Ast.Parameter(Ast.TypePointer(classTypeStruct), "this");
                ParameterList parameterList = Ast.ParameterList(thisParameter);
                // scopeVars.put("this", thisParameter);

                TypeRefList typeRefList = Ast.TypeRefList(Ast.TypePointer(classTypeStruct));
                Type resultType = generateType(md.getReturnType());

                BasicBlock initBlock = Ast.BasicBlock();
                currentBasicBlockList = Ast.BasicBlockList(initBlock);
                for (MJVarDecl vd : md.getFormalParameters()) {
                    Type pType = generateType(vd.getType());
                    Parameter p = Ast.Parameter(pType, vd.getName());
                    parameterList.add(p);
                    typeRefList.add(pType);
                }

                Proc methodCorrespondingProc = Ast.Proc(cd.getName() + "-" + md.getName(), resultType, parameterList,
                        currentBasicBlockList);

                // add proc type to vtablestruct
                // add only if not existing in supertype
                int idx = vtableStruct.getFields().size();

                Optional<StructField> extsf = vtableStruct.getFields().stream()
                        .filter(sf -> sf.getName().equals(md.getName())).findAny();
                if (extsf.isPresent()) {
                    idx = vtableStruct.getFields().indexOf(extsf.get());
                    ProcedureRef oldConst = (ProcedureRef) globalvtableConstList.remove(idx);

                    methodCorrespondingProc.getParameters().clear();
                    methodCorrespondingProc.setParameters(oldConst.getProcedure().getParameters().copy());
                    methodCorrespondingProc.getParameters().set(0, thisParameter);
                    ((TypeProc) ((TypePointer) extsf.get().getType()).getTo()).getArgTypes().set(0,
                            thisParameter.getType());
                    methodCorrespondingProc.setReturnType(oldConst.getProcedure().getReturnType());
                } else
                    vtableStruct.getFields()
                            .add(Ast.StructField(Ast.TypePointer(Ast.TypeProc(typeRefList, resultType)), md.getName()));
                classMethodIndexes.put(cd.getName() + "-" + md.getName(), idx);
                globalvtableConstList.add(idx, Ast.ProcedureRef(methodCorrespondingProc));

                finalProg.getProcedures().add(methodCorrespondingProc);
                methodsProcs.put(md, methodCorrespondingProc);
            }
        });
        alreadyGeneratedMethodProcs.add(cd);
    }

    /**
     * Generates code that initializes the given operand
     * 
     * @param var          the variable to initialize
     * @param deep         true if pointers should be initialized deeply
     * @param contextBlock current block in which to add eventual instructions
     * @return the operand resulting in the initialized variable
     */
    private Operand initializeVariable(Operand var, boolean deep, BasicBlock contextBlock) {
        Type varType = var.calculateType();

        if (varType instanceof TypeInt) {
            return Ast.ConstInt(0);
        }
        if (varType instanceof TypeBool) {
            return Ast.ConstBool(false);
        }

        if (varType instanceof TypePointer) {
            Type pointedType = ((TypePointer) varType).getTo();
            if (pointedType instanceof TypeProc)
                return var;

            if (pointedType instanceof TypeStruct && deep) {
                StructFieldList sfl = ((TypeStruct) ((TypePointer) varType).getTo()).getFields();
                for (int i = 0; i < sfl.size(); i++) {
                    TemporaryVar fieldPointer = Ast.TemporaryVar(sfl.get(i).getName());
                    contextBlock.add(Ast.GetElementPtr(fieldPointer, var.copy(),
                            Ast.OperandList(Ast.ConstInt(0), Ast.ConstInt(i))));
                    Type sftype = sfl.get(i).getType();

                    initializeVariable(Ast.VarRef(fieldPointer), false, contextBlock);
                }
                return var;
            }

            if (pointedType instanceof TypeInt || pointedType instanceof TypeBool) {
                TemporaryVar pointed = Ast.TemporaryVar("pointed");
                contextBlock.add(Ast.Load(pointed, var.copy()));
                contextBlock.add(Ast.Store(var.copy(), initializeVariable(Ast.VarRef(pointed), deep, contextBlock)));
                return var;
            }

            contextBlock.add(Ast.Store(var.copy(), Ast.Nullpointer()));
            return var;

        }
        return var;
    }

    /**
     * Given a classname looks for its declaration
     * 
     * @param name name of the class
     * @return class declaration of the searched class
     */
    private MJClassDecl getClassDeclByName(String name) {
        Optional<MJClassDecl> cd = this.classDeclList.stream().filter(i -> i.getName().equals(name)).findAny();
        return cd.isPresent() ? cd.get() : null;
    }

    public Prog getProg() {
        return this.finalProg;
    }

    public void setAnalysisInfo(Map<MJElement, MJElement> ai) {
        this.typeAnalysisInfo = ai;
    }
}