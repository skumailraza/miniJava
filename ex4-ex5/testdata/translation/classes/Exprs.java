class Exprs {
    public static void main(String[] args) {
        Expr e;
        Add a;
        Number l;
        Number r;
        l = new Number();
        l.value = 4;
        r = new Number();
        r.value = 5;
        a = new Add();
        a.left = l;
        a.right = r;
        e = a;
        System.out.println(e.eval());
    }
}

class Expr {
    int line;
    int column;
    int eval() {
        return 0;
    }
}

class Number extends Expr {
    int value;
    int eval() {
        return value;
    }
}

class Add extends Expr {
    Expr left;
    Expr right;
    int eval() {
        return left.eval() + right.eval();
    }
}