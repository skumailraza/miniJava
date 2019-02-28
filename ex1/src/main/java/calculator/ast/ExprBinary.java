package calculator.ast;

import java.util.Map;

import calculator.ast.finalvalues.FinalValue;
import calculator.ast.finalvalues.Number;

public class ExprBinary extends Expr {
    private Expr left;
    private Expr right;
    private Operator op;

    public ExprBinary(Expr left, Expr right, Operator op) {
        this.left = left;
        this.right = right;
        this.op = op;
    }

    @Override
    public String toString() {
        return "(" + left + " " + op + " " + right + ")";
    }

    public Expr getLeft() {
        return left;
    }

    public Expr getRight() {
        return right;
    }

    public Operator getOp() {
        return op;
    }

    @Override
    public FinalValue evaluate(Map<String, FinalValue> arg) {
        FinalValue evl = left.evaluate(arg), evr = right.evaluate(arg);
        if (!(evl instanceof Number) || !(evr instanceof Number))
            return null;
        int evLeft = ((Number) evl).getValue();
        int evRight = ((Number) evr).getValue();

        switch (op) {
        case Plus:
            return new Number(evLeft + evRight);
        case Minus:
            return new Number(evLeft - evRight);
        case Times:
            return new Number(evLeft * evRight);
        case Div:
            return new Number(evLeft / evRight);
        case Equals:
            return new Number((evLeft == evRight) ? 1 : 0);
        case Less:
            return new Number((evLeft < evRight) ? 1 : 0);
        }
        return null;
    }
}
