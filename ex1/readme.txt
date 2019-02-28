++++++++++++++++++++++++++++++++++
++ First name:Theogene          
++ Last name:urimubenshi
++ Email:turimube@rhrk.uni-kl.de
+++++++++++++++++++++++++++++++++++

++++++++++++++++++++++++++++++++++
++ First name:Nicolas          
++ Last name:Belliard
++ Email:belliard@rhrk.uni-kl.de
+++++++++++++++++++++++++++++++++++

++++++++++++++++++++++++++++++++++
++ First name:Syed Muhammad Kumail          
++ Last name:Raza
++ Email:sraza@rhrk.uni-kl.de
+++++++++++++++++++++++++++++++++++

+++++++++++++++++++++++++++++++++++
++ First name: Mustafa         
++ Last name: Hafidi
++ Email: hafidi@rhrk.uni-kl.de
+++++++++++++++++++++++++++++++++++



Exercise 1)
The multiline problem is solvable using the regular expression: \{-(.|{LineTerminator}|{WhiteSpace})*-\}
Which uses the pre-existing LineTerminator and WhiteSpace definitions to recognize the multiline comments. Also, the lexer recognizes if as a keyword because in the specification the rule that recognizes the keyword "if" is written before the rule that recognizes the identifiers, thus, the lexer selects the first rule.

Exercise 2)
The most trivial way to extend the cup file to support the given grammar is with the following specification:
expr ::=
    LPAREN expr:l PLUS expr:r RPAREN
    {: RESULT = new ExprBinary(l, r, Operator.Plus); :}
  | LPAREN expr:l TIMES expr:r RPAREN
      {: RESULT = new ExprBinary(l, r, Operator.Times); :}
  | LPAREN expr:l MINUS expr:r RPAREN
    {: RESULT = new ExprBinary(l, r, Operator.Minus); :}
  | LPAREN expr:l DIV expr:r RPAREN
      {: RESULT = new ExprBinary(l, r, Operator.Div); :}
  | LPAREN expr:l LESS expr:r RPAREN
      {: RESULT = new ExprBinary(l, r, Operator.Less); :}
  | LPAREN expr:l EQUALS expr:r RPAREN
    {: RESULT = new ExprBinary(l, r, Operator.Equals); :}
    
  | LPAREN expr:l expr:r RPAREN
      {: RESULT = new ExprApplication(l, r); :}
      
  | LPAREN FUN expr:id ARROW expr:b RPAREN
      {: RESULT = new ExprFunction(id, b); :}   
      
  | IF expr:c THEN expr:f ELSE expr:l
      {: RESULT = new ExprConditional(c, f, l); :}    
  | LET expr:id EQ expr:v IN expr:b
      {: RESULT = new ExprLet(id,v, b); :}    
    
  | NUMBER:n
        {: RESULT = new ExprNumber(n); :}
  | ID:id
    {: RESULT = new ExprIdentifier(id); :}
  ;

  However, the specification above described doesn't take in consideration the operators (mathematical and function application) precedence. A correction can be made rewriting the specification by chaining more productions in a mutual way as follows:

  
expr ::= FUN expr:id ARROW expr:b
            {: RESULT = new ExprFunction(id, b); :}   
        | IF expr:c THEN expr:f ELSE expr:l
            {: RESULT = new ExprConditional(c, f, l); :}    
        | LET expr:id EQ expr:v IN expr:b
            {: RESULT = new ExprLet(id,v, b); :}
        | plus:p
            {: RESULT = p; :};

plus ::= plus:l PLUS min:r 
    	    {: RESULT = new ExprBinary(l, r, Operator.Plus); :}
        | min:m
            {: RESULT = m; :};
            
min ::= min:l MINUS mult:r 
    		{: RESULT = new ExprBinary(l, r, Operator.Minus); :}
        | mult:t
            {: RESULT = t; :};

mult ::= mult:l TIMES div:r 
    		{: RESULT = new ExprBinary(l, r, Operator.Times); :}
        | div:t
            {: RESULT = t; :};

div ::= div:l DIV less:r 
    		{: RESULT = new ExprBinary(l, r, Operator.Div); :}
        | less:t
            {: RESULT = t; :};

less ::= less:l LESS equals:r 
    		{: RESULT = new ExprBinary(l, r, Operator.Less); :}
        | equals:t
            {: RESULT = t; :};

equals ::= equals:l EQUALS app:r 
    		{: RESULT = new ExprBinary(l, r, Operator.Equals); :}
        | app:t
            {: RESULT = t; :};
            
app ::= app:l term:r 
    		{: RESULT = new ExprApplication(l, r); :}
        | term:t
            {: RESULT = t; :};
                   

term ::= NUMBER:n
        	{: RESULT = new ExprNumber(n); :}
        | ID:id
            {: RESULT = new ExprIdentifier(id); :}
        | LPAREN expr:e RPAREN
            {: RESULT = e; :};

Exercise 3 and (partially) Exercise 4)
The evaluation process is designed in a polimorphic way, such that every expression evaluates itself in the most appropriate way. The first evaluation call starts in the Main, and then depending on the dynamic type of the expression, the evaluation executes sistematically. The evaluator take in consideration also variables, let and function constructs; but it does not do variable substituion for scoping purposes yet.


