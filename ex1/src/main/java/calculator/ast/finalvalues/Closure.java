package calculator.ast.finalvalues;

import calculator.ast.Expr;

@FunctionalInterface
public interface Closure extends FinalValue {
    FinalValue apply(FinalValue argument);
}
