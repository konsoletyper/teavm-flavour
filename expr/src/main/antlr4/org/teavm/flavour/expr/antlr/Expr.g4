grammar Expr;

NUMBER : INT_NUMBER | FRAC_NUMBER ;
fragment INT_NUMBER : '0' | '1'..'9' ('0'..'9')* ;
fragment FRAC_NUMBER : INT_NUMBER '.' DIGITS EXPONENT? | INT_NUMBER EXPONENT ;
fragment DIGITS : ('0'..'9')+ ;
fragment EXPONENT : ('e' | 'E') ('+' | '-')? DIGITS ;

IDENTIFIER : IDENTIFIER_START IDENTIFIER_PART* ;
fragment IDENTIFIER_START : 'A'..'Z' | 'a'..'z' | '_' | '$' ;
fragment IDENTIFIER_PART : IDENTIFIER_START | '0'..'9' ;

STRING_LITERAL : '\'' STRING_CHAR* '\'' ;
fragment STRING_CHAR : STRING_NON_ESCAPE_CHAR | STRING_ESCAPE_CHAR ;
fragment STRING_NON_ESCAPE_CHAR : ' ' .. '&' | '(' .. '[' | ']' .. '\uFFFF' ;
fragment STRING_ESCAPE_CHAR : '\\' ('r' | 'n' | 't' | '\\' | '\'' | '\\u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT) ;
fragment HEX_DIGIT : [0-9a-fA-F] ;

WHITESPACE : (' ' | '\r' | '\n' | '\t') -> channel(HIDDEN) ;

object
    : '{' (entires+=objectEntry (',' entries+=objectEntry)*)? '}'
    ;

objectEntry
    : key=IDENTIFIER ':' value=lambda
    ;

lambda
    : (boundVars=lambdaBoundVars '->')? body=assignment
    ;
lambdaBoundVars
    : '(' (boundVars+=lambdaBoundVar (',' boundVars+=lambdaBoundVar)*)? ')'
    | boundVars+=lambdaBoundVar
    ;
lambdaBoundVar
    : varType=type? varName=lambdaIdentifier
    ;
lambdaIdentifier
    : '_'
    | IDENTIFIER
    ;

assignment
    : (lhs=path '=')? rhs=ternaryCondition
    ;

expression
    : value=ternaryCondition
    ;

ternaryCondition
    : condition=or ('?' consequent=or ':' alternative=ternaryCondition)?
    ;

or
    : arguments+=and (('or' | '||') arguments+=and)*
    ;
and
    : arguments+=not (('and' | '&&') arguments+=not)*
    ;
not
    : (notKeyword=('not' | '!'))? operand=comparison
    ;

comparison
    : first=additive (operations+=comparisonOperation remaining+=additive)*
    ;
comparisonOperation
    : '=='
    | '!='
    | '<'
    | 'lt'
    | '>'
    | 'gt'
    | '<='
    | 'loe'
    | '>='
    | 'goe'
    ;

additive
    : arguments+=multiplicative (operations+=('+' | '-') arguments+=multiplicative)*
    ;
multiplicative
    : arguments+=arithmetic (operations+=('*' | '/' | '%') arguments+=arithmetic)*
    ;
arithmetic
    : '-' operand=arithmetic #trueArithmetic
    | operand=path #arithmeticFallback
    ;

path
    : '(' targetType=type ')' value=primitive #pathCast
    | base=primitive navigations+=navigation* isInstance=instanceOf? #pathNavigated
    ;
navigation
    : '.' id=IDENTIFIER (invoke='(' arguments=expressionList? ')')? #propertyNavigation
    | '[' index=expression ']' #arrayNavigation
    ;

instanceOf
    : 'instanceof' checkedType=genericType
    ;

primitive
    : value=NUMBER #numberPrimitive
    | value=STRING_LITERAL #stringPrimitive
    | 'this' #thisPrimitive
    | 'true' #truePrimitive
    | 'false' #falsePrimitive
    | 'null' #nullPrimitive
    | functionName=IDENTIFIER '(' arguments=expressionList? ')' #functionCall
    | id=IDENTIFIER #idPrimitive
    | '(' value=expression ')' #parenthesized
    ;

expressionList
    : expressions+=lambda (',' expressions+=lambda)*
    ;

type
    : baseType=nonArrayType suffix=arraySuffix?
    ;
genericType
    : baseType=qualifiedClassType suffix=arraySuffix?
    ;
arraySuffix
    : (suffice+='[' ']')+
    ;
nonArrayType
    : 'boolean' #booleanType
    | 'char'    #charType
    | 'byte'    #byteType
    | 'short'   #shortType
    | 'int'     #intType
    | 'long'    #longType
    | 'float'   #floatType
    | 'double'  #doubleType
    | qualifiedClassType #classType
    ;
qualifiedClassType
    : raw=rawClassType ('<' args=typeArguments '>')?
    ;
rawClassType
    : fqnPart+=IDENTIFIER ('.' fqnPart+=IDENTIFIER)*
    ;
typeArguments
    : types+=genericType (',' types+=genericType)*
    ;