package calculator.ast;

import java.util.Map;

import calculator.ast.finalvalues.FinalValue;
import calculator.ast.finalvalues.Number;

public class ExprConditional extends Expr {
    Expr condition, first, last;

    public ExprConditional(Expr condition, Expr first, Expr last) {
        this.condition = condition;
        this.first = first;
        this.last = last;
    }

    @Override
    public String toString() {
        return "(if " + condition + " then " + first + " else " + last + ")";
    }

    @Override
    public FinalValue evaluate(Map<String, FinalValue> ctx) {
        FinalValue cev = condition.evaluate(ctx);
        return (cev instanceof Number && ((Number) cev).getValue() == 1) ? first.evaluate(ctx) : last.evaluate(ctx);
    }
}
