/*
 *  Copyright 2015 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.flavour.expr;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.teavm.flavour.expr.antlr.ExprBaseVisitor;
import org.teavm.flavour.expr.antlr.ExprLexer;
import org.teavm.flavour.expr.antlr.ExprParser;
import org.teavm.flavour.expr.ast.AssignmentExpr;
import org.teavm.flavour.expr.ast.BinaryExpr;
import org.teavm.flavour.expr.ast.BinaryOperation;
import org.teavm.flavour.expr.ast.BoundVariable;
import org.teavm.flavour.expr.ast.CastExpr;
import org.teavm.flavour.expr.ast.ConstantExpr;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.ast.InstanceOfExpr;
import org.teavm.flavour.expr.ast.InvocationExpr;
import org.teavm.flavour.expr.ast.LambdaExpr;
import org.teavm.flavour.expr.ast.ObjectEntry;
import org.teavm.flavour.expr.ast.ObjectExpr;
import org.teavm.flavour.expr.ast.PropertyExpr;
import org.teavm.flavour.expr.ast.StaticInvocationExpr;
import org.teavm.flavour.expr.ast.StaticPropertyExpr;
import org.teavm.flavour.expr.ast.TernaryConditionExpr;
import org.teavm.flavour.expr.ast.ThisExpr;
import org.teavm.flavour.expr.ast.UnaryExpr;
import org.teavm.flavour.expr.ast.UnaryOperation;
import org.teavm.flavour.expr.ast.VariableExpr;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.NullType;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.PrimitiveArray;
import org.teavm.flavour.expr.type.TypeArgument;
import org.teavm.flavour.expr.type.ValueType;

public class Parser {
    private ClassResolver classes;
    private List<Diagnostic> diagnostics = new ArrayList<>();

    public Parser(ClassResolver classes) {
        this.classes = classes;
    }

    public ObjectExpr parseObject(String text) {
        diagnostics.clear();
        try (Reader reader = new StringReader(text)) {
            ExprLexer lexer = new ExprLexer(new ANTLRInputStream(reader));
            lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
            lexer.addErrorListener(errorListener);
            ExprParser exprParser = new ExprParser(new CommonTokenStream(lexer));
            exprParser.removeErrorListener(ConsoleErrorListener.INSTANCE);
            exprParser.addErrorListener(errorListener);
            return exprParser.object().accept(objectVisitor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Expr parse(String text) {
        diagnostics.clear();
        try (Reader reader = new StringReader(text)) {
            ExprLexer lexer = new ExprLexer(new ANTLRInputStream(reader));
            lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
            lexer.addErrorListener(errorListener);
            ExprParser exprParser = new ExprParser(new CommonTokenStream(lexer));
            exprParser.removeErrorListener(ConsoleErrorListener.INSTANCE);
            exprParser.addErrorListener(errorListener);
            return exprParser.lambda().accept(exprVisitor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    private BaseErrorListener errorListener = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
                String msg, RecognitionException e) {
            int start = 0;
            int end = 0;
            if (offendingSymbol instanceof Token) {
                Token offendingToken = (Token) offendingSymbol;
                start = offendingToken.getStartIndex();
                end = offendingToken.getStopIndex();
            }
            diagnostics.add(new Diagnostic(start, end, msg));
        }
    };


    private <T extends Expr> T withLocation(T expr, ParserRuleContext ctx) {
        return withLocation(expr, ctx, ctx);
    }

    private <T extends Expr> T withLocation(T expr, ParserRuleContext startCtx, ParserRuleContext endCtx) {
        expr.setStart(startCtx.getStart().getStartIndex());
        expr.setEnd(endCtx.getStop().getStopIndex() + 1);
        return expr;
    }

    private final ExprBaseVisitor<ObjectExpr> objectVisitor = new ExprBaseVisitor<ObjectExpr>() {
        @Override
        public ObjectExpr visitObject(ExprParser.ObjectContext ctx) {
            ObjectExpr object = new ObjectExpr();
            for (ExprParser.ObjectEntryContext entryContext : ctx.entires) {
                ObjectEntry entry = new ObjectEntry();
                entry.setKey(entryContext.key.getText());
                entry.setValue(entryContext.value.accept(exprVisitor));
                object.getEntries().add(entry);
            }
            return object;
        }
    };

    private final ExprBaseVisitor<Expr> exprVisitor = new ExprBaseVisitor<Expr>() {
        @Override
        public Expr visitLambda(ExprParser.LambdaContext ctx) {
            if (ctx.body == null) {
                return new ConstantExpr(null);
            }
            Expr body = ctx.body.accept(this);
            if (ctx.boundVars == null) {
                return body;
            }
            List<BoundVariable> boundVars = ctx.boundVars.boundVars.stream()
                    .map(boundVar -> boundVar.accept(boundVarVisitor))
                    .collect(Collectors.toList());

            return withLocation(new LambdaExpr(body, boundVars), ctx);
        }

        @Override
        public Expr visitAssignment(ExprParser.AssignmentContext ctx) {
            if (ctx.rhs == null) {
                return null;
            }
            Expr rhs = ctx.rhs.accept(this);
            if (ctx.lhs == null) {
                return rhs;
            }
            return withLocation(new AssignmentExpr(ctx.lhs.accept(this), rhs), ctx);
        }

        @Override
        public Expr visitExpression(ExprParser.ExpressionContext ctx) {
            return ctx.value.accept(this);
        }

        @Override
        public Expr visitTernaryCondition(ExprParser.TernaryConditionContext ctx) {
            Expr condition = ctx.condition.accept(this);
            if (ctx.consequent == null) {
                return condition;
            }
            Expr consequent = ctx.consequent.accept(this);
            Expr alternative = ctx.alternative.accept(this);
            return withLocation(new TernaryConditionExpr(condition, consequent, alternative), ctx);
        }

        @Override
        public Expr visitOr(ExprParser.OrContext ctx) {
            Expr result = ctx.arguments.get(0).accept(this);
            for (int i = 1; i < ctx.arguments.size(); ++i) {
                ExprParser.AndContext rhsContext = ctx.arguments.get(i);
                Expr rhs = rhsContext.accept(this);
                result = withLocation(new BinaryExpr(result, rhs, BinaryOperation.OR), ctx, rhsContext);
            }
            return result;
        }

        @Override
        public Expr visitAnd(ExprParser.AndContext ctx) {
            Expr result = ctx.arguments.get(0).accept(this);
            for (int i = 1; i < ctx.arguments.size(); ++i) {
                ExprParser.NotContext rhsContext = ctx.arguments.get(i);
                Expr rhs = rhsContext.accept(this);
                result = withLocation(new BinaryExpr(result, rhs, BinaryOperation.AND), ctx, rhsContext);
            }
            return result;
        }

        @Override
        public Expr visitNot(ExprParser.NotContext ctx) {
            Expr result = ctx.operand.accept(this);
            if (ctx.notKeyword != null) {
                result = withLocation(new UnaryExpr(result, UnaryOperation.NOT), ctx);
            }
            return result;
        }

        @Override
        public Expr visitComparison(ExprParser.ComparisonContext ctx) {
            Expr result = ctx.first.accept(this);
            for (int i = 0; i < ctx.remaining.size(); ++i) {
                BinaryOperation operation;
                switch (ctx.operations.get(i).getText()) {
                    case "==":
                        operation = BinaryOperation.EQUAL;
                        break;
                    case "!=":
                        operation = BinaryOperation.NOT_EQUAL;
                        break;
                    case "<":
                    case "less":
                        operation = BinaryOperation.LESS;
                        break;
                    case "<=":
                    case "loe":
                        operation = BinaryOperation.LESS_OR_EQUAL;
                        break;
                    case ">":
                    case "greater":
                        operation = BinaryOperation.GREATER;
                        break;
                    case ">=":
                    case "goe":
                        operation = BinaryOperation.GREATER_OR_EQUAL;
                        break;
                    default:
                        throw new AssertionError("Unknown token: " + ctx.operations.get(i).getText());
                }
                ExprParser.AdditiveContext rhsContext = ctx.remaining.get(i);
                result = withLocation(new BinaryExpr(result, rhsContext.accept(this), operation), ctx, rhsContext);
            }
            return result;
        }

        @Override
        public Expr visitAdditive(ExprParser.AdditiveContext ctx) {
            Expr result = ctx.arguments.get(0).accept(this);
            for (int i = 0; i < ctx.operations.size(); ++i) {
                BinaryOperation operation;
                switch (ctx.operations.get(i).getText()) {
                    case "+":
                        operation = BinaryOperation.ADD;
                        break;
                    case "-":
                        operation = BinaryOperation.SUBTRACT;
                        break;
                    default:
                        throw new AssertionError("Unknown token: " + ctx.operations.get(i).getText());
                }
                ExprParser.MultiplicativeContext rhsContext = ctx.arguments.get(i + 1);
                result = withLocation(new BinaryExpr(result, rhsContext.accept(this), operation), ctx, rhsContext);
            }

            return result;
        }

        @Override
        public Expr visitMultiplicative(ExprParser.MultiplicativeContext ctx) {
            Expr result = ctx.arguments.get(0).accept(this);
            for (int i = 0; i < ctx.operations.size(); ++i) {
                BinaryOperation operation;
                switch (ctx.operations.get(i).getText()) {
                    case "*":
                        operation = BinaryOperation.MULTIPLY;
                        break;
                    case "/":
                        operation = BinaryOperation.DIVIDE;
                        break;
                    case "%":
                        operation = BinaryOperation.REMAINDER;
                        break;
                    default:
                        throw new AssertionError("Unknown token: " + ctx.operations.get(i).getText());
                }
                ExprParser.ArithmeticContext rhsContext = ctx.arguments.get(i + 1);
                result = withLocation(new BinaryExpr(result, rhsContext.accept(this), operation), ctx, rhsContext);
            }

            return result;
        }

        @Override
        public Expr visitTrueArithmetic(ExprParser.TrueArithmeticContext ctx) {
            Expr operand = ctx.operand.accept(this);
            return withLocation(new UnaryExpr(operand, UnaryOperation.NEGATE), ctx);
        }

        @Override
        public Expr visitArithmeticFallback(ExprParser.ArithmeticFallbackContext ctx) {
            return ctx.operand.accept(this);
        }

        @Override
        public Expr visitPathCast(ExprParser.PathCastContext ctx) {
            Expr value = ctx.value.accept(this);
            ValueType type = ctx.targetType.accept(typeVisitor);
            return withLocation(new CastExpr(value, type), ctx);
        }

        @Override
        public Expr visitPathNavigated(ExprParser.PathNavigatedContext ctx) {
            Expr base = ctx.base.accept(this);
            NavigationVisitor navigationVisitor = new NavigationVisitor(ctx, base);
            for (ExprParser.NavigationContext navigationContext : ctx.navigations) {
                navigationContext.accept(navigationVisitor);
            }
            Expr result = navigationVisitor.expr;
            if (result == null) {
                int start = ctx.getStart().getStartIndex();
                int end = ctx.getStop().getStopIndex();
                diagnostics.add(new Diagnostic(start, end, "Reference to class. Static property access supposed?"));
                return new ConstantExpr(null);
            }
            if (ctx.isInstance != null) {
                GenericType type = (GenericType) ctx.isInstance.checkedType.accept(typeVisitor);
                result = withLocation(new InstanceOfExpr(result, type), ctx, ctx.isInstance);
            }
            return result;
        }

        @Override
        public Expr visitNumberPrimitive(ExprParser.NumberPrimitiveContext ctx) {
            String text = ctx.getText();
            Object value;
            if (text.contains(".")) {
                value = Double.parseDouble(text);
            } else {
                value = Integer.parseInt(text);
            }
            return withLocation(new ConstantExpr(value), ctx);
        }

        @Override
        public Expr visitStringPrimitive(ExprParser.StringPrimitiveContext ctx) {
            StringBuilder sb = new StringBuilder();
            String text = ctx.getText();
            for (int i = 1; i < text.length() - 1; ++i) {
                char c = text.charAt(i);
                if (c != '\\') {
                    sb.append(c);
                } else {
                    c = text.charAt(++i);
                    switch (c) {
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case '\\':
                        case '\'':
                            sb.append(c);
                            break;
                        case 'u':
                            int code = 0;
                            for (int j = 0; j < 4; ++j) {
                                code *= 16;
                                code += Character.digit(text.charAt(++i), 16);
                            }
                            sb.append((char) code);
                            break;
                    }
                }
            }
            return withLocation(new ConstantExpr(sb.toString()), ctx);
        }

        @Override
        public Expr visitThisPrimitive(ExprParser.ThisPrimitiveContext ctx) {
            return withLocation(new ThisExpr(), ctx);
        }

        @Override
        public Expr visitTruePrimitive(ExprParser.TruePrimitiveContext ctx) {
            return withLocation(new ConstantExpr(true), ctx);
        }

        @Override
        public Expr visitFalsePrimitive(ExprParser.FalsePrimitiveContext ctx) {
            return withLocation(new ConstantExpr(false), ctx);
        }

        @Override
        public Expr visitNullPrimitive(ExprParser.NullPrimitiveContext ctx) {
            return withLocation(new ConstantExpr(null), ctx);
        }

        @Override
        public Expr visitIdPrimitive(ExprParser.IdPrimitiveContext ctx) {
            return withLocation(new VariableExpr(ctx.getText()), ctx);
        }

        @Override
        public Expr visitFunctionCall(ExprParser.FunctionCallContext ctx) {
            String name = ctx.functionName.getText();
            List<Expr> arguments = Collections.emptyList();
            if (ctx.arguments != null) {
                arguments = ctx.arguments.expressions.stream()
                        .map(arg -> arg.accept(exprVisitor))
                        .collect(Collectors.toList());
            }
            return withLocation(new InvocationExpr(null, name, arguments), ctx);
        }

        @Override
        public Expr visitParenthesized(ExprParser.ParenthesizedContext ctx) {
            return ctx.value.accept(this);
        }
    };

    private class NavigationVisitor extends ExprBaseVisitor<Void> {
        private ParserRuleContext topLevelContext;
        private Expr expr;
        private StringBuilder fqnBuilder = new StringBuilder();
        private boolean fqnFinished;
        private String className;

        NavigationVisitor(ParserRuleContext topLevelContext, Expr expr) {
            this.topLevelContext = topLevelContext;
            if (expr instanceof VariableExpr) {
                String name = ((VariableExpr) expr).getName();
                fqnBuilder.append(name);
                if (!tryResolve()) {
                    this.expr = expr;
                    fqnFinished = true;
                }
            } else {
                this.expr = expr;
                fqnFinished = true;
            }
        }

        @Override
        public Void visitArrayNavigation(ExprParser.ArrayNavigationContext ctx) {
            expr = withLocation(new BinaryExpr(expr, ctx.index.accept(exprVisitor), BinaryOperation.GET_ELEMENT),
                    topLevelContext, ctx);
            return null;
        }

        @Override
        public Void visitPropertyNavigation(ExprParser.PropertyNavigationContext ctx) {
            String propertyName = ctx.id.getText();
            if (ctx.invoke != null) {
                List<Expr> arguments = Collections.emptyList();
                if (ctx.arguments != null) {
                    arguments = ctx.arguments.expressions.stream()
                            .map(arg -> arg.accept(exprVisitor))
                            .collect(Collectors.toList());
                }
                if (className != null) {
                    expr = new StaticInvocationExpr(className, propertyName, arguments);
                    className = null;
                } else {
                    expr = new InvocationExpr(expr, propertyName, arguments);
                }
                fqnFinished = true;
            } else {
                fqnBuilder.append(".").append(propertyName);
                if (!tryResolve()) {
                    if (className != null) {
                        expr = new StaticPropertyExpr(className, propertyName);
                    } else {
                        expr = new PropertyExpr(expr, propertyName);
                    }
                }
            }
            if (expr != null) {
                expr = withLocation(expr, topLevelContext, ctx);
            }
            return null;
        }

        public boolean tryResolve() {
            if (fqnFinished) {
                return false;
            }
            String fqn = fqnBuilder.toString();
            String className = classes.findClass(fqn);
            if (className != null) {
                this.className = className;
            } else {
                fqnFinished = true;
            }
            return className != null;
        }
    }

    private final ExprBaseVisitor<BoundVariable> boundVarVisitor = new ExprBaseVisitor<BoundVariable>() {
        @Override
        public BoundVariable visitLambdaBoundVar(ExprParser.LambdaBoundVarContext ctx) {
            ValueType type = null;
            if (ctx.varType != null) {
                type = ctx.varType.accept(typeVisitor);
            }
            String varName = ctx.varName.getText();
            return new BoundVariable(varName.equals("_") ? "" : varName, type);
        }
    };

    private final ExprBaseVisitor<ValueType> typeVisitor = new ExprBaseVisitor<ValueType>() {
        @Override
        public ValueType visitType(ExprParser.TypeContext ctx) {
            ValueType type = ctx.baseType.accept(this);
            int arrayDegree = 0;
            if (ctx.suffix != null) {
                arrayDegree = ctx.suffix.accept(arraySuffixVisitor);
            }
            while (arrayDegree-- > 0) {
                if (type instanceof Primitive) {
                    type = new PrimitiveArray((Primitive) type);
                } else {
                    type = new GenericArray((GenericType) type);
                }
            }
            return type;
        }

        @Override
        public ValueType visitGenericType(ExprParser.GenericTypeContext ctx) {
            ValueType type = ctx.baseType.accept(this);
            int arrayDegree = 0;
            if (ctx.suffix != null) {
                arrayDegree = ctx.suffix.accept(arraySuffixVisitor);
            }
            while (arrayDegree-- > 0) {
                if (type instanceof Primitive) {
                    type = new PrimitiveArray((Primitive) type);
                } else {
                    type = new GenericArray((GenericType) type);
                }
            }
            return type;
        }

        @Override
        public ValueType visitBooleanType(ExprParser.BooleanTypeContext ctx) {
            return Primitive.BOOLEAN;
        }

        @Override
        public ValueType visitCharType(ExprParser.CharTypeContext ctx) {
            return Primitive.CHAR;
        }

        @Override
        public ValueType visitByteType(ExprParser.ByteTypeContext ctx) {
            return Primitive.BYTE;
        }

        @Override
        public ValueType visitShortType(ExprParser.ShortTypeContext ctx) {
            return Primitive.SHORT;
        }

        @Override
        public ValueType visitIntType(ExprParser.IntTypeContext ctx) {
            return Primitive.INT;
        }

        @Override
        public ValueType visitLongType(ExprParser.LongTypeContext ctx) {
            return Primitive.LONG;
        }

        @Override
        public ValueType visitFloatType(ExprParser.FloatTypeContext ctx) {
            return Primitive.FLOAT;
        }

        @Override
        public ValueType visitDoubleType(ExprParser.DoubleTypeContext ctx) {
            return Primitive.DOUBLE;
        }

        @Override
        public ValueType visitQualifiedClassType(ExprParser.QualifiedClassTypeContext ctx) {
            String fqn = ctx.raw.fqnPart.stream()
                    .map(part -> part.getText())
                    .collect(Collectors.joining("."));
            String className = classes.findClass(fqn);

            if (className == null) {
                int start = ctx.getStart().getStartIndex();
                int end = ctx.getStop().getStopIndex() + 1;
                diagnostics.add(new Diagnostic(start, end, "Unknown class " + ctx.getText()));
                return NullType.INSTANCE;
            }

            List<TypeArgument> args = Collections.emptyList();
            if (ctx.args != null) {
                args = ctx.args.types.stream()
                        .map(arg -> TypeArgument.invariant((GenericType) arg.accept(this)))
                        .collect(Collectors.toList());
            }
            return new GenericClass(className, args);
        }

        @Override
        public ValueType visitRawClassType(ExprParser.RawClassTypeContext ctx) {
            String fqn = ctx.fqnPart.stream()
                    .map(part -> part.getText())
                    .collect(Collectors.joining("."));
            String className = classes.findClass(fqn);
            if (className != null) {
                return new GenericClass(className);
            } else {
                int start = ctx.getStart().getStartIndex();
                int end = ctx.getStop().getStopIndex();
                diagnostics.add(new Diagnostic(start, end, "Unknown class " + ctx.getText()));
                return NullType.INSTANCE;
            }
        }
    };

    private final ExprBaseVisitor<Integer> arraySuffixVisitor = new ExprBaseVisitor<Integer>() {
        @Override
        public Integer visitArraySuffix(ExprParser.ArraySuffixContext ctx) {
            return ctx.suffice.size();
        }
    };
}
