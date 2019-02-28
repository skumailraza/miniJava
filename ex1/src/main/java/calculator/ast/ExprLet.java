package calculator.ast;

import java.util.Map;

import calculator.ast.finalvalues.FinalValue;

public class ExprLet extends Expr {
    Expr variable, value, body;

    public ExprLet(Expr variable, Expr value, Expr body) {
        this.variable = variable;
        this.value = value;
        this.body = body;
    }

    @Override
    public String toString() {
        return "(let " + variable + " = " + value + " in " + body + ")";
    }

    @Override
    public FinalValue evaluate(Map<String, FinalValue> ctx) {
        ctx.put(variable.toString(), value.evaluate(ctx));
        FinalValue res = body.evaluate(ctx);
        ctx.remove(variable.toString());
        return res;
    }
}
