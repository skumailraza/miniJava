package calculator.ast;

import java.util.Map;

import calculator.ast.finalvalues.FinalValue;

public abstract class Expr {

    public abstract FinalValue evaluate(Map<String, FinalValue> context);

}
