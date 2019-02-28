package analysis;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.lang.model.util.ElementScanner6;

import minijava.ast.*;

public class AnalysisVisitor extends MJElement.DefaultVisitor {

    List<TypeError> typeErrors = new ArrayList<TypeError>();

    MJMainClass mainClass;
    MJClassDeclList classDeclList;
    boolean inheritanceLoop;

    MJClassDecl currentClassDecl;
    MJMethodDecl currentMethodDecl;

    Map<String, List<String>> currentClassCache;

    Map<String, MJType> blockContext;

    // return the last
    public AnalysisVisitor(List<TypeError> typeErrors) {
        this.typeErrors = typeErrors;

        this.blockContext = new LinkedHashMap<>();
        this.currentClassCache = new HashMap<>();
        this.currentClassCache.put("methods", new ArrayList<String>());
        this.currentClassCache.put("fields", new ArrayList<String>());
    }

    @Override
    public void visit(MJProgram program) {
        this.mainClass = program.getMainClass();
        this.classDeclList = program.getClassDecls();

        // check if mainclass is exten
        if (getClassDeclByName(this.mainClass.getName()) != null) {
            typeErrors.add(new TypeError(program, "Cannot extend the main class"));
        }
        this.inheritanceLoop = checkInheritanceLoop();

        program.getMainClass().accept(this);
        program.getClassDecls().accept(this);
    }

    @Override
    public void visit(MJMainClass mainClass) {
        blockContext.put(mainClass.getArgsName(), null);
        mainClass.getMainBody().accept(this);
        this.blockContext.clear();
    }

    @Override
    public void visit(MJClassDeclList classDeclList) {

        // check class inheritance loop
        if (this.inheritanceLoop) {
            typeErrors.add(new TypeError(classDeclList, "Loop Detected in the class hierarchy"));
            return;
        }

        // Check class duplicate
        for (MJClassDecl i1 : classDeclList) {
            for (MJClassDecl i2 : classDeclList) {
                if (i1 != i2 && i1.getName().equals(i2.getName())) {
                    typeErrors.add(new TypeError(classDeclList, "Duplicate class declaration detected"));
                    break;
                }
            }
        }

        for (MJClassDecl i : classDeclList) {
            i.accept(this);
        }
    }

    @Override
    public void visit(MJMethodDecl methodDecl) {
        this.currentMethodDecl = methodDecl;
        // checking for override
        MJVarDeclList formalParameters = methodDecl.getFormalParameters();
        if (!(this.currentClassDecl.getExtended() instanceof MJExtendsNothing)) {

            Optional<MJMethodDecl> md = getMethodDeclByName(
                    ((MJExtendsClass) this.currentClassDecl.getExtended()).getName(), methodDecl.getName());

            if (md.isPresent()) {
                // checking if formalparameters are the same
                MJVarDeclList superFormalParameters = md.get().getFormalParameters();
                boolean formalParametersEqual = formalParameters.size() == superFormalParameters.size()
                        && IntStream.range(0, formalParameters.size()).allMatch(i -> superFormalParameters.get(i)
                                .getType().structuralEquals(formalParameters.get(i).getType()));

                // checking if return type is subtype of the overrided method
                boolean returnTypeOk = isSubtype(methodDecl.getReturnType(), md.get().getReturnType());
                if (!formalParametersEqual || !returnTypeOk) {
                    typeErrors.add(new TypeError(methodDecl,
                            "Invalid method overriding, formal parameters must have same types and return types must be covariant"));
                }
            }
        }

        // adding variable declarations to this block scope
        int oSize = blockContext.size();

        for (MJVarDecl variable : formalParameters) {

            if (blockContext.containsKey(variable.getName()))
                typeErrors.add(new TypeError(formalParameters,
                        "Using an existing identifier in the formal parameters of the method " + methodDecl.getName()));
            typeCheck(variable);

        }
        methodDecl.getFormalParameters().accept(this);

        typeCheck(methodDecl.getReturnType());
        methodDecl.getReturnType().accept(this);

        methodDecl.getMethodBody().accept(this);
        shrinkContext(oSize);
    }

    @Override
    public void visit(MJBlock block) {
        for (MJStatement i : block) {
            typeCheck(i);
        }
    }

    @Override
    public void visit(MJClassDecl classDecl) {
        this.currentClassDecl = classDecl;
        this.currentClassCache.put("methods", new ArrayList<String>());
        this.currentClassCache.put("fields", new ArrayList<String>());

        classDecl.getExtended().accept(this);

        MJVarDeclList classFields = classDecl.getFields();
        for (MJVarDecl field : classFields) {
            if (blockContext.containsKey(field.getName()))
                typeErrors.add(new TypeError(classFields, "Cannot declare two fields with same identifier"));
            else {
                blockContext.put(field.getName(), field.getType());
                this.currentClassCache.get("fields").add(field.getName());
            }
        }

        classDecl.getFields().accept(this);

        MJMethodDeclList classMethods = classDecl.getMethods();
        for (MJMethodDecl method : classMethods) {
            if (this.currentClassCache.get("methods").contains(method.getName()))
                typeErrors.add(new TypeError(classMethods, "Duplicate method name detected"));
            else
                this.currentClassCache.get("methods").add(method.getName());
        }
        classDecl.getMethods().accept(this);

        this.currentClassCache.clear();
        this.blockContext.clear();
    }

    @Override
    public void visit(MJExprList exprList) {
        for (MJExpr i : exprList) {
            typeCheck(i);
            i.accept(this);
        }
    }

    /**
     * Checks whether a given type is valid, in particolar it checks if the
     * corresponding class exists (in the case of MJTypeClass)
     * 
     * @param type the type to check
     * @return
     */
    private Optional<MJType> typeCheck(MJType type) {
        return type.match(new MJType.Matcher<Optional<MJType>>() {

            @Override
            public Optional<MJType> case_TypeInt(MJTypeInt typeInt) {
                return Optional.of(typeInt);
            }

            @Override
            public Optional<MJType> case_TypeBool(MJTypeBool typeBool) {
                return Optional.of(typeBool);
            }

            @Override
            public Optional<MJType> case_TypeClass(MJTypeClass typeClass) {
                // checking if class type exists
                Optional<MJClassDecl> corrCD = classDeclList.stream()
                        .filter(cd -> cd.getName().equals(typeClass.getName())).findAny();
                return corrCD.isPresent() ? Optional.of(typeClass) : Optional.empty();
            }

            @Override
            public Optional<MJType> case_TypeArray(MJTypeArray typeArray) {
                return Optional.of(typeArray);
            }
        });
    }

    /**
     * Checks whether a given statment is well typed
     * 
     * @param stmt the statment for which to check the type
     */
    private void typeCheck(MJStatement stmt) {
        stmt.match(new MJStatement.MatcherVoid() {

            @Override
            public void case_StmtWhile(MJStmtWhile stmtWhile) {
                Optional<MJType> conditionType = typeCheck(stmtWhile.getCondition());
                if (!(conditionType.get() instanceof MJTypeBool)) {
                    typeErrors.add(new TypeError(stmtWhile, "While guard has to be of boolean type"));
                }
                typeCheck(stmtWhile.getLoopBody());
            }

            @Override
            public void case_StmtExpr(MJStmtExpr stmtExpr) {
                typeCheck(stmtExpr.getExpr());
            }

            @Override
            public void case_VarDecl(MJVarDecl varDecl) {
                if (blockContext.containsKey(varDecl.getName())
                        && !currentClassCache.get("fields").contains(varDecl.getName())) {
                    typeErrors
                            .add(new TypeError(varDecl, "Trying to declare a variable with an existing variable name"));
                    return;
                }
                typeCheck(varDecl.getType()).ifPresent(vtype -> blockContext.put(varDecl.getName(), vtype));

            }

            @Override
            public void case_StmtAssign(MJStmtAssign stmtAssign) {
                Optional<MJType> leftType = typeCheck(stmtAssign.getAddress());
                Optional<MJType> rightType = typeCheck(stmtAssign.getRight());
                if (!leftType.isPresent() || !rightType.isPresent())
                    return;
                if (!isSubtype(rightType.get(), leftType.get())) {
                    typeErrors.add(new TypeError(stmtAssign, "Cannot cast from type " + rightType.get().toString()
                            + " to " + leftType.get().toString() + " in the assignment"));
                }

            }

            @Override
            public void case_StmtPrint(MJStmtPrint stmtPrint) {
                Optional<MJType> ptype = typeCheck(stmtPrint.getPrinted());
                if (ptype.isPresent() && !(ptype.get() instanceof MJTypeInt)) {
                    typeErrors.add(new TypeError(stmtPrint, "Cannot print a non-integer value"));
                }
            }

            @Override
            public void case_StmtIf(MJStmtIf stmtIf) {
                Optional<MJType> conditionType = typeCheck(stmtIf.getCondition());
                if (conditionType.isPresent() && !(conditionType.get() instanceof MJTypeBool)) {
                    typeErrors.add(new TypeError(stmtIf, "if statments must have a boolean condition"));
                }
                typeCheck(stmtIf.getIfTrue());
                typeCheck(stmtIf.getIfFalse());
            }

            @Override
            public void case_Block(MJBlock block) {
                int oldSize = blockContext.size();
                for (MJStatement i : block) {
                    typeCheck(i);
                }
                shrinkContext(oldSize);

            }

            @Override
            public void case_StmtReturn(MJStmtReturn stmtReturn) {
                Optional<MJType> resultType = typeCheck(stmtReturn.getResult());
                if (currentMethodDecl == null) {
                    typeErrors.add(new TypeError(stmtReturn, "Unexpected return expression in main method"));
                    return;
                }
                // checking if method returns a subtype of the return type
                if (resultType.isPresent() && !isSubtype(resultType.get(), currentMethodDecl.getReturnType())) {
                    typeErrors.add(
                            new TypeError(stmtReturn, "The returned expression must be a subtype of the return type"));
                }
            }
        });
    }

    /**
     * Checks whether a given expression is well typed and returns its type
     * 
     * @param expr an address expression
     * @return an optional containing the type of expression
     */
    private Optional<MJType> typeCheck(MJExprL expr) {
        return expr.match(new MJExprL.Matcher<Optional<MJType>>() {

            @Override
            public Optional<MJType> case_ArrayLookup(MJArrayLookup arrayLookup) {
                Optional<MJType> arrayExprType = typeCheck(arrayLookup.getArrayExpr());
                // checking the type of the array expression
                if (!arrayExprType.isPresent() || !(arrayExprType.get() instanceof MJTypeArray)) {
                    typeErrors.add(new TypeError(arrayLookup, "Cannot lookup on a non-array type"));
                    return Optional.empty();
                }

                Optional<MJType> indexExprType = typeCheck(arrayLookup.getArrayIndex());
                // checking the type of the index expression
                if (!indexExprType.isPresent() || !(indexExprType.get() instanceof MJTypeInt)) {
                    typeErrors.add(new TypeError(arrayLookup, "Expected an integer as index for array lookups"));
                    return Optional.empty();
                }

                return Optional.of(((MJTypeArray) arrayExprType.get()).getComponentType());
            }

            @Override
            public Optional<MJType> case_VarUse(MJVarUse varUse) {
                Optional<MJVarDecl> fieldDecl = currentClassDecl != null
                        ? getFieldDeclByName(currentClassDecl.getName(), varUse.getVarName())
                        : Optional.empty();
                if (!blockContext.containsKey(varUse.getVarName()) && !fieldDecl.isPresent()) {

                    typeErrors
                            .add(new TypeError(varUse, "Use of the undeclared variable (" + varUse.getVarName() + ")"));
                    return Optional.empty();
                }

                if (blockContext.containsKey(varUse.getVarName()))
                    return Optional.of(blockContext.get(varUse.getVarName()));
                else
                    return Optional.of(fieldDecl.get().getType());
            }

            @Override
            public Optional<MJType> case_FieldAccess(MJFieldAccess fieldAccess) {
                Optional<MJType> receiverType = typeCheck(fieldAccess.getReceiver());
                // checking if the receiver is of type class
                if (!receiverType.isPresent() || !(receiverType.get() instanceof MJTypeClass)) {
                    typeErrors.add(new TypeError(fieldAccess, "Cannot access field on a non-class type"));
                    return Optional.empty();
                }

                // checking if the class receiver has the requested field
                MJClassDecl cd = getClassDeclByName(((MJTypeClass) receiverType.get()).getName());
                Optional<MJVarDecl> correspondingVD = cd.getFields().stream()
                        .filter(vd -> vd.getName().equals(fieldAccess.getFieldName())).findAny();
                if (!correspondingVD.isPresent()) {
                    typeErrors.add(new TypeError(fieldAccess, "Cannot find the requested field '"
                            + fieldAccess.getFieldName() + "' in the class " + cd.getName()));
                    return Optional.empty();
                }

                return Optional.of(correspondingVD.get().getType());
            }

        });
    }

    /**
     * Checks whether a given expression is well typed and returns its type
     * 
     * @param expr an expression
     * @return an optional containing the type of the expression (the type may not
     *         exist if the expression is not well typed)
     */
    private Optional<MJType> typeCheck(MJExpr expr) {
        return expr.match(new MJExpr.Matcher<Optional<MJType>>() {

            @Override
            public Optional<MJType> case_Number(MJNumber number) {
                return Optional.of(MJ.TypeInt());
            }

            @Override
            public Optional<MJType> case_ExprUnary(MJExprUnary exprUnary) {
                return exprUnary.getUnaryOperator().match(new MJUnaryOperator.Matcher<Optional<MJType>>() {

                    @Override
                    public Optional<MJType> case_UnaryMinus(MJUnaryMinus unaryMinus) {
                        // check if the operand expression has int type
                        Optional<MJType> expType = typeCheck(exprUnary.getExpr());
                        if (expType.isPresent() && (expType.get() instanceof MJTypeInt)) {
                            return Optional.of(expType.get());
                        }

                        // adding type error
                        typeErrors.add(new TypeError(unaryMinus,
                                "Impossible to apply unary minus on a non number expression"));
                        return Optional.empty();
                    }

                    @Override
                    public Optional<MJType> case_Negate(MJNegate negate) {
                        // check if the operand expression has int type
                        Optional<MJType> expType = typeCheck(exprUnary.getExpr());
                        if (expType.isPresent() && (expType.get() instanceof MJTypeBool)) {
                            return Optional.of(expType.get());
                        }

                        // adding type error
                        typeErrors.add(new TypeError(negate,
                                "Impossible to apply negate operator on a non boolean expression"));
                        return Optional.empty();
                    }
                });
            }

            @Override
            public Optional<MJType> case_NewObject(MJNewObject newObject) {
                return Optional.of(MJ.TypeClass(newObject.getClassName()));
            }

            @Override
            public Optional<MJType> case_MethodCall(MJMethodCall methodCall) {
                Optional<MJType> receiverType = typeCheck(methodCall.getReceiver());
                if (!receiverType.isPresent() || !(receiverType.get() instanceof MJTypeClass)) {
                    typeErrors.add(new TypeError(methodCall, "Impossible to call a method on primitive type"));
                    return Optional.empty();
                }

                // getting method signature to check return type
                Optional<MJMethodDecl> md = getMethodDeclByName(((MJTypeClass) receiverType.get()).getName(),
                        methodCall.getMethodName());

                if (!md.isPresent()) {
                    typeErrors.add(new TypeError(methodCall,
                            "The method called cannot be find in the class " + receiverType.get()));
                    return Optional.empty();
                }
                // checking if argument list type is coerent
                MJExprList elist = methodCall.getArguments();
                if (elist.size() != md.get().getFormalParameters().size()) {
                    typeErrors.add(new TypeError(methodCall,
                            "The number of the given arguments does not coincide with the number of formal parameters of the method "
                                    + methodCall.getMethodName()));
                    return Optional.empty();
                }
                for (int i = 0; i < elist.size(); i++) {
                    MJVarDecl vd = md.get().getFormalParameters().get(i);
                    Optional<MJType> prType = typeCheck(elist.get(i));

                    MJType vdType = vd.getType();

                    if (!prType.isPresent() || !isSubtype(prType.get(), vd.getType())) {
                        typeErrors.add(new TypeError(methodCall,
                                "The arguments types do not coincide with the formal parameters types"));
                        return Optional.empty();
                    }
                }

                return Optional.of(md.get().getReturnType());

            }

            @Override
            public Optional<MJType> case_Read(MJRead read) {
                return typeCheck(read.getAddress());
            }

            @Override
            public Optional<MJType> case_ExprNull(MJExprNull exprNull) {
                return Optional.of(new NullType());
            }

            @Override
            public Optional<MJType> case_BoolConst(MJBoolConst boolConst) {
                return Optional.of(MJ.TypeBool());
            }

            @Override
            public Optional<MJType> case_ArrayLength(MJArrayLength arrayLength) {
                Optional<MJType> arrayExprType = typeCheck(arrayLength.getArrayExpr());
                // checking the type of the array expression
                if (!arrayExprType.isPresent() || !(arrayExprType.get() instanceof MJTypeArray)) {
                    typeErrors.add(new TypeError(arrayLength, "Cannot get length of a non-array type"));
                    return Optional.empty();
                }

                return Optional.of(MJ.TypeInt());
            }

            @Override
            public Optional<MJType> case_ExprThis(MJExprThis exprThis) {
                if (currentClassDecl != null)
                    return Optional.of(MJ.TypeClass(currentClassDecl.getName()));
                typeErrors.add(new TypeError(exprThis, "Cannot use 'this' keyword in a static method"));
                return Optional.empty();
            }

            @Override
            public Optional<MJType> case_ExprBinary(MJExprBinary exprBinary) {
                // checking left and right expressions types
                Optional<MJType> leftType = typeCheck(exprBinary.getLeft());
                Optional<MJType> rightType = typeCheck(exprBinary.getRight());

                if (!leftType.isPresent() || !rightType.isPresent())
                    return Optional.empty();

                return exprBinary.getOperator().match(new MJOperator.Matcher<Optional<MJType>>() {
                    private Optional<MJType> intOperator() {
                        if (!(leftType.get() instanceof MJTypeInt) || !(rightType.get() instanceof MJTypeInt)) {
                            typeErrors.add(new TypeError(exprBinary, "Expected numeric operands for applying the "
                                    + exprBinary.getOperator().toString() + " operator"));
                            return Optional.empty();
                        }
                        return Optional.of(MJ.TypeInt());
                    }

                    @Override
                    public Optional<MJType> case_Plus(MJPlus plus) {
                        return intOperator();
                    }

                    @Override
                    public Optional<MJType> case_Minus(MJMinus minus) {
                        return intOperator();
                    }

                    @Override
                    public Optional<MJType> case_Less(MJLess less) {
                        if (leftType.get() instanceof MJTypeInt && rightType.get() instanceof MJTypeInt) {
                            return Optional.of(MJ.TypeBool());
                        }
                        typeErrors.add(new TypeError(less, "Cannot compare non integer values using less operator"));
                        return Optional.empty();
                    }

                    @Override
                    public Optional<MJType> case_And(MJAnd and) {
                        if (!(leftType.get() instanceof MJTypeBool) || !(rightType.get() instanceof MJTypeBool)) {
                            typeErrors.add(
                                    new TypeError(exprBinary, "Cannot apply AND operator to non boolean operands"));
                            return Optional.empty();
                        }
                        return Optional.of(MJ.TypeBool());
                    }

                    @Override
                    public Optional<MJType> case_Div(MJDiv div) {
                        return intOperator();
                    }

                    @Override
                    public Optional<MJType> case_Times(MJTimes times) {
                        return intOperator();
                    }

                    @Override
                    public Optional<MJType> case_Equals(MJEquals equals) {
                        if (!leftType.get().structuralEquals(rightType.get())) {
                            typeErrors.add(new TypeError(exprBinary,
                                    "Operands must have same type for applying the equals operator"));
                            return Optional.empty();
                        }
                        return Optional.of(MJ.TypeBool());
                    }
                });

            }

            @Override
            public Optional<MJType> case_NewArray(MJNewArray newArray) {
                Optional<MJType> arraySizeType = typeCheck(newArray.getArraySize());
                Optional<MJType> baseType = typeCheck(newArray.getBaseType());
                if (!baseType.isPresent())
                    typeErrors.add(new TypeError(newArray, "Invalid base type in the new array expression"));
                if (arraySizeType.isPresent() && arraySizeType.get() instanceof MJTypeInt)
                    return Optional.of(MJ.TypeArray(newArray.getBaseType().copy()));

                typeErrors.add(new TypeError(newArray, "The array size must be numeric"));
                return Optional.empty();
            }
        });
    }

    /**
     * Checks whether type1 is a subtype of type2
     * 
     * @param type1 the left type in the subtype relation
     * @param type2 the right type in the subtype relation
     * @return true if type1 is subtype of type 2, false otherwise
     */
    private boolean isSubtype(MJType type1, MJType type2) {
        if (type1 instanceof NullType) {
            return type2 instanceof MJTypeArray || type2 instanceof MJTypeClass;
        }
        return type1.structuralEquals(type2) || type1.match(new MJType.Matcher<Boolean>() {

            @Override
            public Boolean case_TypeInt(MJTypeInt typeInt) {
                return type1.structuralEquals(type2);
            }

            @Override
            public Boolean case_TypeClass(MJTypeClass typeClass) {
                return type2 instanceof MJTypeClass && checkTypeInheritance(getClassDeclByName(typeClass.getName()),
                        ((MJTypeClass) type2).getName());
            }

            @Override
            public Boolean case_TypeBool(MJTypeBool typeBool) {
                return type1.structuralEquals(type2);
            }

            @Override
            public Boolean case_TypeArray(MJTypeArray typeArray) {
                return type1.structuralEquals(type2);
            }
        });
    }

    /**
     * Searches for a method declaration by name, looking also in super types
     * 
     * @param className  the class from which to start looking for the method
     * @param methodName the searched method name
     * @return returns an optional (the method may not exist) containing the
     *         searched method declaration
     */
    private Optional<MJMethodDecl> getMethodDeclByName(String className, String methodName) {
        MJClassDecl cd = getClassDeclByName(className);
        Optional<MJMethodDecl> md = cd.getMethods().stream().filter(m -> m.getName().equals(methodName)).findAny();
        if (!md.isPresent()) {
            return cd.getExtended().match(new MJExtended.Matcher<Optional<MJMethodDecl>>() {
                @Override
                public Optional<MJMethodDecl> case_ExtendsNothing(MJExtendsNothing extendsNothing) {
                    return Optional.empty();
                }

                @Override
                public Optional<MJMethodDecl> case_ExtendsClass(MJExtendsClass extendsClass) {
                    return getMethodDeclByName(extendsClass.getName(), methodName);
                }
            });
        }
        return md;
    }

    /**
     * Searches for a field declaration by name, looking also in super types
     * 
     * @param className the class from which to start looking for the field
     * @param fieldName the searched field name
     * @return returns an optional (the field may not exist) containing the searched
     *         field declaration
     */
    private Optional<MJVarDecl> getFieldDeclByName(String className, String fieldName) {
        MJClassDecl cd = getClassDeclByName(className);
        Optional<MJVarDecl> fd = cd.getFields().stream().filter(f -> f.getName().equals(fieldName)).findAny();
        if (!fd.isPresent()) {
            return cd.getExtended().match(new MJExtended.Matcher<Optional<MJVarDecl>>() {
                @Override
                public Optional<MJVarDecl> case_ExtendsNothing(MJExtendsNothing extendsNothing) {
                    return Optional.empty();
                }

                @Override
                public Optional<MJVarDecl> case_ExtendsClass(MJExtendsClass extendsClass) {
                    return getFieldDeclByName(extendsClass.getName(), fieldName);
                }
            });
        }
        return fd;
    }

    private MJClassDecl getClassDeclByName(String name) {
        Optional<MJClassDecl> cd = this.classDeclList.stream().filter(i -> i.getName().equals(name)).findAny();
        return cd.isPresent() ? cd.get() : null;
    }

    private boolean checkTypeInheritance(MJClassDecl A, String B) {
        if (this.inheritanceLoop)
            return false;
        if (!(A.getExtended() instanceof MJExtendsNothing)) {
            MJClassDecl extendedClass = getClassDeclByName(((MJExtendsClass) A.getExtended()).getName());
            if (extendedClass.getName().equals(B))
                return true;
            else
                return checkTypeInheritance(extendedClass, B);
        }
        return false;
    }

    private boolean checkInheritanceLoopRec(MJClassDecl classDecl, int step) {
        if (step > classDeclList.size() || classDecl == null)
            return true;

        if (!(classDecl.getExtended() instanceof MJExtendsNothing)) {
            String extendedClass = ((MJExtendsClass) classDecl.getExtended()).getName();
            return checkInheritanceLoopRec(getClassDeclByName(extendedClass), step + 1);
        }
        return false;
    }

    private boolean checkInheritanceLoop() {
        this.classDeclList = classDeclList;
        boolean foundCycle = false;
        for (MJClassDecl i : classDeclList)
            foundCycle = foundCycle || checkInheritanceLoopRec(i, 0);
        return foundCycle;

    }

    private void shrinkContext(int start) {
        Object[] arrContext = blockContext.entrySet().toArray();
        for (int i = start; i < arrContext.length; i++) {
            blockContext.remove(((Entry<String, MJType>) arrContext[i]).getKey());
        }
    }
}
