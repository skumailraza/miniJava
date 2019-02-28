package calculator.ast;

import java.util.Map;

import calculator.ast.finalvalues.FinalValue;

public class ExprParen extends Expr {
    Expr expr;

    public ExprParen(Expr expr) {
        this.expr = expr;
    }

    @Override
    public String toString() {
        return "(" + expr + ")";
    }

    @Override
    public FinalValue evaluate(Map<String, FinalValue> ctx) {
        return this.expr.evaluate(ctx);
    }

}
