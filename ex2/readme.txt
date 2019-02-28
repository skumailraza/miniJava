Grammer Description and explanation:

It wasn't necessary to create additional classes or utils functions for the AST. We used the given static functionalities to generate the ast nodes.
In the cup file it's possible to see the resulting grammar, which is similar to the definition given in the exercise sheet, with some modification for solving ambiguities and associativity.
In particular, we used the cup keyword precedence to specify the precedences of the tokens solving several conflicts, ambiguities and associativity problems.

For example,
for Boolean expression an identifier and an integer literal, the precedence is in the said order and they are non-associative.
All the other literals like AND, EQUALS, LESS, LPAREN are left associative.


However, even with this precedence specification, the operator associativity wouldn't be respected if we leave the "op" production present in the grammar definition. For example for the following grammer rule:
expr ::= expr:e1 op:op expr:e2
    |   NEG expr:e3
    |   MINUS expr:e4
    |   expr:e5 DOT LENGTH
    |   expr:e6 DOT ID:id LPAREN exprList RPAREN
    |   expr:e6 DOT ID:id LPAREN RPAREN
    |   TRUE
    |   FALSE
    |   INT:i
    |   THIS
    |   NULL
    |   NEW baseType:b LBRACKET expr:e RBRACKET LRBRACKET
    |   NEW ID:id LPAREN RPAREN
    |   LPAREN expr:e RPAREN
    |   expr1:el
        {: RESULT = el; :}
    ;

This is because for "op" the LexerGenerator will look for a production rule and will follow that production rule overriding the precedence and the associativity described in the beginning.

Therefore we got rid of the 'op' production from the grammer and introduced the following chained production rule:

exprRed ::= expr:e1 AND expr:e2
        {: RESULT = ExprBinary(e1, And(), e2); :}
    | expr:e1 PLUS expr:e2
        {: RESULT = ExprBinary(e1, Plus(), e2); :}
    | expr:e1 MINUS expr:e2
        {: RESULT = ExprBinary(e1, Minus(), e2); :}
    | expr:e1 TIMES expr:e2
        {: RESULT = ExprBinary(e1, Times(), e2); :}
    | expr:e1 LESS expr:e2
        {: RESULT = ExprBinary(e1, Less(), e2); :}
    | expr:e1 DIV expr:e2
        {: RESULT = ExprBinary(e1, Div(), e2); :}
    | expr:e1 EQUALS expr:e2
        {: RESULT = ExprBinary(e1, Equals(), e2); :}

    |   NEG expr:e
        {: RESULT = ExprUnary(Negate(), e); :}
    |   MINUS expr:e
        {: RESULT = ExprUnary(UnaryMinus(), e); :}
    |   expr:e DOT LENGTH
        {: RESULT = ArrayLength(e); :}
    |   expr:e DOT identifier:id LPAREN exprList:el RPAREN
        {: RESULT = MethodCall(e, id, el); :}
    |   TRUE
        {: RESULT = BoolConst(true); :}
    .
    .
    .

expr ::= exprRed:e
        {: RESULT = e; :}
    |
    .
    .
    .


For the lists e.g

classDeclList, varDeclList, methodDeclList, etc.

we used recursive definitions such that the order of the elements is preserved even after the parsing.
Also we introduced an 'empty' and 'compty' (which is comma or empty) rule to the grammer since
any of these lists can be empty or have a 'COMMA' after each item in the list.

The ExprL has to be treated as an address as this stores the reference(or dereference) of the identifier to the right, which becomes an assignment statement.


