package exprs;

import java_cup.runtime.*;
import java_cup.runtime.ComplexSymbolFactory.Location;
import static exprs.ExprParserSym.*;
import java.io.Reader;
import java.util.function.Consumer;
      
%%
   
/* -----------------Options and Declarations Section----------------- */
   
%public
%class Lexer

%unicode
%cup
%line
%column



// Code between %{ and %}, both of which must be at the beginning of a
// line, will be copied letter to letter into the lexer class source.
// Here you declare member variables and functions that are used inside
// scanner actions.  
%{   
    private ComplexSymbolFactory symbolFactory;

    public Lexer(ComplexSymbolFactory symbolFactory, Reader input){
	    this(input);
        this.symbolFactory = symbolFactory;
    }

    private Symbol symbol(int code){
        String name = ExprParserSym.terminalNames[code];
        Location left = new Location(yyline+1,yycolumn+1-yylength());
        Location right = new Location(yyline+1,yycolumn+1);
	    return symbolFactory.newSymbol(name, code, left, right);
    }
    
    private Symbol symbol(int code, String lexem){
        String name = ExprParserSym.terminalNames[code];
        Location left = new Location(yyline+1,yycolumn+1-yylength());
        Location right = new Location(yyline+1,yycolumn+1);
	    return symbolFactory.newSymbol(name, code, left, right, lexem);	   
    }

%}
   


// Macro Declarations:
// These declarations are regular expressions that will be used latter
// in the Lexical Rules Section.  
   
LineTerminator = \r|\n|\r\n
WhiteSpace     = {LineTerminator} | [ \t\f]
Number = 0 | [1-9][0-9]*
Identifier = [a-zA-Z][a-z0-9A-Z_]*
SingleLineComment = --.*{LineTerminator}
MultiLineComment = \{-(.|{LineTerminator}|{WhiteSpace})*-\}

%%
/* ------------------------Lexical Rules Section---------------------- */
   
  
<YYINITIAL> {
   
    "+"                { return symbol(PLUS); }
    "-"                { return symbol(MINUS); }
    "*"                { return symbol(TIMES); }
    "/"                { return symbol(DIV); }
    "=="               { return symbol(EQUALS); }
    "<"                { return symbol(LESS); }
    "if"               { return symbol(IF); }
    "then"             { return symbol(THEN); }
    "else"             { return symbol(ELSE); }
    "let"              { return symbol(LET); }
    "="                { return symbol(EQ); }
    "in"               { return symbol(IN); }
    "fun"              { return symbol(FUN); }
    "->"               { return symbol(ARROW); }
    "("                { return symbol(LPAREN); }
    ")"                { return symbol(RPAREN); }
   
    {Number}           { return symbol(NUMBER, yytext()); }
    {Identifier}	   { return symbol(ID, yytext()); }
    {SingleLineComment} { /* skip comment */ }
    {MultiLineComment}  {/* skip multiline comment*/ }
    {WhiteSpace}       { /* skip whitespace */ }
}


/* All unmatched inputs produce an error: */
[^]                    { return symbol(INVALID_TOKEN, yytext()); }
<<EOF>>                { return symbol(EOF); }
