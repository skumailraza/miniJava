package calculator.ast;

import java.util.Map;

import calculator.ast.finalvalues.Closure;
import calculator.ast.finalvalues.FinalValue;

public class ExprFunction extends Expr implements FinalValue {
    Expr arg, body;

    public ExprFunction(Expr arg, Expr body) {
        this.arg = arg;
        this.body = body;
    }

    @Override
    public String toString() {
        return "(fun " + arg + " -> " + body + ")";
    }

    public String getArg() {
        return arg.toString();
    }

    public Expr getBody() {
        return body;
    }

    @Override
    public ExprFunction evaluate(Map<String, FinalValue> ctx) {
        return this;
    }

}
