package analysis;

import java.util.*;
import minijava.ast.*;

public class AnalysisVisitor extends MJElement.DefaultVisitor {

    List<TypeError> typeErrors = new ArrayList<TypeError>();

    MJMainClass mainClass;
    MJClassDeclList classDeclList;
    MJClassDecl currentClassDecl;

    Map<String, List<String>> currentScope;

    @Override
    public void visit(MJProgram program) {
        this.mainClass = program.getMainClass();
        this.classDeclList = program.getClassDecls();

        // check if mainclass is extended
        if (getClassDeclByName(this.mainClass.getName(), this.classDeclList) != null) {
            typeErrors.add(new TypeError(program, "Extended main class error"));
        }

        program.getMainClass().accept(this);
        program.getClassDecls().accept(this);
    }

    @Override
    public void visit(MJClassDeclList classDeclList) {

        // check class inheritance loop
        checkInheritanceLoop(classDeclList);

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

        // checking for override
        MJVarDeclList formalParameters = methodDecl.getFormalParameters();
        if (!(this.currentClassDecl.getExtended() instanceof MJExtendsNothing)) {

            MJClassDecl extendedClass = getClassDeclByName(
                    ((MJExtendsClass) this.currentClassDecl.getExtended()).getName(), this.classDeclList);

            // check for return type
            if (extendedClass.getMethods().stream().filter(meth -> {
                boolean par = true, returnTok = true;
                for (int i = 0; i < meth.getFormalParameters().size() && par; i++) {
                    if (formalParameters.get(i) == null)
                        par = false;
                    if (!formalParameters.get(i).getType().getClass()
                            .equals(meth.getFormalParameters().get(i).getType().getClass()))
                        par = false;
                }
                returnTok = meth.getReturnType().getClass().equals(methodDecl.getReturnType().getClass());
                if (meth.getReturnType() instanceof MJTypeClass && methodDecl.getReturnType() instanceof MJTypeClass) {
                    String t1 = ((MJTypeClass) meth.getReturnType()).getName();
                    String t2 = ((MJTypeClass) methodDecl.getReturnType()).getName();
                    returnTok = checkTypeInheritance(getClassDeclByName(t2, this.classDeclList), t1);
                }

                return !(meth.getName().equals(methodDecl.getName()) && par
                        && meth.getFormalParameters().size() == formalParameters.size() && returnTok);
            }).findAny().isPresent()) {
                // override check
                typeErrors.add(new TypeError(methodDecl, "Invalid method override"));
            }
        }
        for (MJVarDecl variable : formalParameters) {
            if (this.currentScope.get("variables").contains(variable.getName()))
                typeErrors.add(new TypeError(formalParameters, "Duplicate variable name detected"));
            else
                this.currentScope.get("variables").add(variable.getName());
        }

        methodDecl.getReturnType().accept(this);
        methodDecl.getFormalParameters().accept(this);
        methodDecl.getMethodBody().accept(this);
    }

    @Override
    public void visit(MJClassDecl classDecl) {
        this.currentClassDecl = classDecl;
        this.currentScope = new HashMap<>();
        this.currentScope.put("methods", new ArrayList<String>());
        this.currentScope.put("fields", new ArrayList<String>());
        this.currentScope.put("variables", new ArrayList<String>());

        classDecl.getExtended().accept(this);

        MJVarDeclList classFields = classDecl.getFields();
        for (MJVarDecl field : classFields) {
            if (this.currentScope.get("fields").contains(field.getName()))
                typeErrors.add(new TypeError(classFields, "Duplicate field name detected"));
            else
                this.currentScope.get("fields").add(field.getName());
        }
        classDecl.getFields().accept(this);

        MJMethodDeclList classMethods = classDecl.getMethods();
        for (MJMethodDecl method : classMethods) {
            if (this.currentScope.get("methods").contains(method.getName()))
                typeErrors.add(new TypeError(classMethods, "Duplicate method name detected"));
            else
                this.currentScope.get("methods").add(method.getName());
        }
        classDecl.getMethods().accept(this);

        this.currentScope.clear();
    }

    @Override
    public void visit(MJVarDecl varDecl) {
        if (this.mainClass.getArgsName().equals(varDecl.getName()))
            typeErrors.add(new TypeError(varDecl, "Duplicate variable name detected"));
        varDecl.getType().accept(this);
    }

    private MJClassDecl getClassDeclByName(String name, MJClassDeclList cdl) {
        for (MJClassDecl i : cdl)
            if (i.getName().equals(name))
                return i;
        return null;
    }

    @Override
    public void visit(MJBlock block) {
        for (MJStatement i : block) {
            /*
             * if (i instanceof MJBlock) typeErrors.add(new TypeError(block,
             * "Block error detected"));
             */
            i.match(new MJStatement.MatcherVoid() {

                @Override
                public void case_StmtAssign(MJStmtAssign stmtAssign) {
                }

                @Override
                public void case_Block(MJBlock block) {
                    for (MJStatement statement : block) {
                        statement.match(this);
                    }
                }

                @Override
                public void case_StmtReturn(MJStmtReturn stmtReturn) {
                }

                @Override
                public void case_StmtPrint(MJStmtPrint stmtPrint) {
                    if (stmtPrint.getPrinted() instanceof MJBoolConst) {
                        typeErrors.add(new TypeError(block, "Print error detected"));
                    }
                }

                @Override
                public void case_StmtWhile(MJStmtWhile stmtWhile) {
                    stmtWhile.getLoopBody().match(this);
                }

                @Override
                public void case_StmtExpr(MJStmtExpr stmtExpr) {
                }

                @Override
                public void case_VarDecl(MJVarDecl varDecl) {

                }

                @Override
                public void case_StmtIf(MJStmtIf stmtIf) {
                    stmtIf.getIfTrue().match(this);
                    stmtIf.getIfFalse().match(this);
                }

            });
            i.accept(this);

        }
    }

    private boolean checkTypeInheritance(MJClassDecl A, String B) {
        if (!(A.getExtended() instanceof MJExtendsNothing)) {
            MJClassDecl extendedClass = getClassDeclByName(((MJExtendsClass) A.getExtended()).getName(),
                    this.classDeclList);
            if (extendedClass.getName().equals(B))
                return true;
            else
                return checkTypeInheritance(extendedClass, B);
        }
        return false;
    }

    private void checkInheritanceLoopRec(MJClassDecl classDecl, int step) {
        if (step > classDeclList.size() || classDecl == null) {
            typeErrors.add(new TypeError(classDecl, "Loop Detected in the class hierarchy"));
            return;
        }
        if (!(classDecl.getExtended() instanceof MJExtendsNothing)) {
            String extendedClass = ((MJExtendsClass) classDecl.getExtended()).getName();
            checkInheritanceLoopRec(getClassDeclByName(extendedClass, classDeclList), step + 1);
        }
    }

    private void checkInheritanceLoop(MJClassDeclList classDeclList) {
        this.classDeclList = classDeclList;
        for (MJClassDecl i : classDeclList)
            checkInheritanceLoopRec(i, 0);

    }

    // return the last
    public AnalysisVisitor(List<TypeError> typeErrors) {
        this.typeErrors = typeErrors;
    }

}
