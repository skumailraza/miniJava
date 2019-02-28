package calculator.ast;

import java.util.Map;

import calculator.ast.finalvalues.Closure;
import calculator.ast.finalvalues.FinalValue;

public class ExprApplication extends Expr {
    Expr left, right;

    public ExprApplication(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public String toString() {
        return "(" + left + " " + right + ")";
    }

    @Override
    public FinalValue evaluate(Map<String, FinalValue> arg) {
        FinalValue evl = left.evaluate(arg);
        if (!(evl instanceof ExprFunction))
            return null;
        FinalValue evr = right.evaluate(arg);

        ExprFunction closure = (ExprFunction) evl;
        arg.put(closure.getArg(), evr);
        FinalValue res = closure.getBody().evaluate(arg);
        arg.remove(closure.getArg());
        return res;
    }

}
