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

import java.util.*;
import org.teavm.flavour.expr.ast.*;
import org.teavm.flavour.expr.plan.*;
import org.teavm.flavour.expr.type.*;

/**
 *
 * @author Alexey Andreev
 */
class CompilerVisitor implements ExprVisitorStrict<TypedPlan> {
    private static final GenericClass booleanClass = new GenericClass("java.lang.Boolean");
    private static final GenericClass characterClass = new GenericClass("java.lang.Character");
    private static final GenericClass byteClass = new GenericClass("java.lang.Byte");
    private static final GenericClass shortClass = new GenericClass("java.lang.Short");
    private static final GenericClass integerClass = new GenericClass("java.lang.Integer");
    private static final GenericClass longClass = new GenericClass("java.lang.Long");
    private static final GenericClass floatClass = new GenericClass("java.lang.Float");
    private static final GenericClass doubleClass = new GenericClass("java.lang.Double");
    private static final GenericClass stringClass = new GenericClass("java.lang.String");
    private static final Set<ValueType> classesSuitableForArrayIndex = new HashSet<>(Arrays.<ValueType>asList(
            characterClass, byteClass, shortClass, integerClass, Primitive.BYTE,
            Primitive.CHAR, Primitive.SHORT, Primitive.INT));
    private static final Set<ValueType> classesSuitableForComparison = new HashSet<>(Arrays.<ValueType>asList(
            characterClass, byteClass, shortClass, integerClass, longClass,
            floatClass, doubleClass, Primitive.BYTE, Primitive.CHAR, Primitive.SHORT,
            Primitive.INT, Primitive.LONG, Primitive.FLOAT, Primitive.DOUBLE));
    private static final Map<Primitive, GenericClass> primitivesToWrappers = new HashMap<>();
    private static final Map<GenericClass, Primitive> wrappersToPrimitives = new HashMap<>();

    private GenericTypeNavigator navigator;
    private Scope scope;
    private List<Diagnostic> diagnostics = new ArrayList<>();
    private TypeVar nullType = new TypeVar();
    private GenericReference nullTypeRef = new GenericReference(nullType);
    private ClassResolver classResolver;

    static {
        primitiveAndWrapper(Primitive.BOOLEAN, booleanClass);
        primitiveAndWrapper(Primitive.CHAR, characterClass);
        primitiveAndWrapper(Primitive.BYTE, byteClass);
        primitiveAndWrapper(Primitive.SHORT, shortClass);
        primitiveAndWrapper(Primitive.INT, integerClass);
        primitiveAndWrapper(Primitive.LONG, longClass);
        primitiveAndWrapper(Primitive.FLOAT, floatClass);
        primitiveAndWrapper(Primitive.DOUBLE, doubleClass);
    }

    static void primitiveAndWrapper(Primitive primitive, GenericClass wrapper) {
        primitivesToWrappers.put(primitive, wrapper);
        wrappersToPrimitives.put(wrapper, primitive);
    }

    public CompilerVisitor(GenericTypeNavigator navigator, ClassResolver classes, Scope scope) {
        this.navigator = navigator;
        this.classResolver = classes;
        this.scope = scope;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    @Override
    public void visit(BinaryExpr<TypedPlan> expr) {
        Expr<TypedPlan> firstOperand = expr.getFirstOperand();
        Expr<TypedPlan> secondOperand = expr.getSecondOperand();
        firstOperand.acceptVisitor(this);
        secondOperand.acceptVisitor(this);
        switch (expr.getOperation()) {
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case REMAINDER: {
                ArithmeticType type = getAritmeticTypeForPair(firstOperand, secondOperand);
                BinaryPlan plan = new BinaryPlan(firstOperand.getAttribute().plan, secondOperand.getAttribute().plan,
                        getPlanType(expr.getOperation()), type);
                expr.setAttribute(new TypedPlan(plan, getType(type)));
                break;
            }
            case AND:
            case OR: {
                ensureBooleanType(firstOperand);
                ensureBooleanType(secondOperand);
                LogicalBinaryPlan plan = new LogicalBinaryPlan(firstOperand.getAttribute().plan,
                        secondOperand.getAttribute().plan, getLogicalPlanType(expr.getOperation()));
                expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
                break;
            }
            case EQUAL:
            case NOT_EQUAL: {
                if (classesSuitableForComparison.contains(firstOperand.getAttribute().type) &&
                        classesSuitableForComparison.contains(secondOperand.getAttribute().type)) {
                    ArithmeticType type = getAritmeticTypeForPair(firstOperand, secondOperand);
                    BinaryPlan plan = new BinaryPlan(firstOperand.getAttribute().plan,
                            secondOperand.getAttribute().plan, getPlanType(expr.getOperation()), type);
                    expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
                } else {
                    ReferenceEqualityPlan plan = new ReferenceEqualityPlan(firstOperand.getAttribute().plan,
                            secondOperand.getAttribute().plan,
                            expr.getOperation() == BinaryOperation.EQUAL ? ReferenceEqualityPlanType.EQUAL :
                                    ReferenceEqualityPlanType.NOT_EQUAL);
                    expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
                }
                break;
            }
            case LESS:
            case LESS_OR_EQUAL:
            case GREATER:
            case GREATER_OR_EQUAL: {
                ArithmeticType type = getAritmeticTypeForPair(firstOperand, secondOperand);
                BinaryPlan plan = new BinaryPlan(firstOperand.getAttribute().plan, secondOperand.getAttribute().plan,
                        getPlanType(expr.getOperation()), type);
                expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
                break;
            }
            case GET_ELEMENT:
                compileGetElement(expr);
                break;
            case ADD:
                compileAdd(expr);
                break;
        }
    }

    private void compileAdd(BinaryExpr<TypedPlan> expr) {
        Expr<TypedPlan> firstOperand = expr.getFirstOperand();
        ValueType firstType = firstOperand.getAttribute().type;
        Expr<TypedPlan> secondOperand = expr.getSecondOperand();
        ValueType secondType = secondOperand.getAttribute().type;
        if (firstType.equals(stringClass) || secondType.equals(stringClass)) {
            Plan firstPlan = firstOperand.getAttribute().plan;
            if (firstPlan instanceof InvocationPlan) {
                InvocationPlan invocation = (InvocationPlan)firstPlan;
                if (invocation.getClassName().equals("java.lang.StringBuilder") &&
                        invocation.getMethodName().equals("toString")) {
                    convertToString(secondOperand);
                    Plan instance = invocation.getInstance();
                    InvocationPlan append = new InvocationPlan("java.lang.StringBuilder", "append",
                            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", instance,
                            secondOperand.getAttribute().plan);
                    invocation.setInstance(append);
                    expr.setAttribute(new TypedPlan(invocation, stringClass));
                    return;
                }
            }
            convertToString(firstOperand);
            convertToString(secondOperand);
            ConstructionPlan construction = new ConstructionPlan("java.lang.StringBuilder", "()V");
            InvocationPlan invocation = new InvocationPlan("java.lang.StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", construction,
                    firstOperand.getAttribute().plan);
            invocation = new InvocationPlan("java.lang.StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", invocation,
                    secondOperand.getAttribute().plan);
            invocation = new InvocationPlan("java.lang.StringBuilder", "toString", "()Ljava/lang/String;",
                    invocation);
            expr.setAttribute(new TypedPlan(invocation, stringClass));
        } else {
            ArithmeticType type = getAritmeticTypeForPair(firstOperand, secondOperand);
            BinaryPlan plan = new BinaryPlan(firstOperand.getAttribute().plan, secondOperand.getAttribute().plan,
                    BinaryPlanType.ADD, type);
            expr.setAttribute(new TypedPlan(plan, getType(type)));
        }
    }

    private void compileGetElement(BinaryExpr<TypedPlan> expr) {
        Expr<TypedPlan> firstOperand = expr.getFirstOperand();
        ValueType firstType = firstOperand.getAttribute().type;
        Expr<TypedPlan> secondOperand = expr.getSecondOperand();
        ValueType secondType = secondOperand.getAttribute().type;
        if (firstType instanceof GenericArray && classesSuitableForArrayIndex.contains(secondType)) {
            GenericArray arrayType = (GenericArray)firstType;
            ensureIntType(secondOperand);
            GetArrayElementPlan plan = new GetArrayElementPlan(firstOperand.getAttribute().plan,
                    secondOperand.getAttribute().plan);
            expr.setAttribute(new TypedPlan(plan, arrayType.getElementType()));
            return;
        } else if (firstType instanceof GenericClass) {
            GenericClass mapClass = navigator.getGenericClass("java.util.Map");
            GenericClass listClass = navigator.getGenericClass("java.util.List");
            TypeUnifier unifier = new TypeUnifier(navigator.getClassRepository());
            if (unifier.unify(mapClass, (GenericType)firstType, true)) {
                TypeVar var = ((GenericReference)mapClass.getArguments().get(1)).getVar();
                GenericType returnType = unifier.getSubstitutions().get(var);
                InvocationPlan plan = new InvocationPlan("java.util.Map", "get",
                        "(Ljava/lang/Object;)Ljava/lang/Object;",
                        firstOperand.getAttribute().plan, secondOperand.getAttribute().plan);
                expr.setAttribute(new TypedPlan(plan, returnType));
                return;
            } else if (unifier.unify(listClass, (GenericType)firstType, false)) {
                TypeVar var = ((GenericReference)listClass.getArguments().get(0)).getVar();
                GenericType returnType = unifier.getSubstitutions().get(var);
                ensureIntType(secondOperand);
                InvocationPlan plan = new InvocationPlan("java.util.List", "get", "(I)Ljava/lang/Object;",
                        firstOperand.getAttribute().plan, secondOperand.getAttribute().plan);
                expr.setAttribute(new TypedPlan(plan, returnType));
                return;
            }
        }
        expr.setAttribute(new TypedPlan(new ConstantPlan(null), new GenericClass("java.lang.Object")));
        error(expr, "Can't apply subscript operator to " + firstType + " with argument of "  + secondType);
    }

    @Override
    public void visit(CastExpr<TypedPlan> expr) {
        expr.setTargetType(resolveType(expr.getTargetType(), expr));
        expr.getValue().acceptVisitor(this);
        Expr<TypedPlan> value = expr.getValue();
        TypedPlan plan = value.getAttribute();
        plan = tryCast(plan, expr.getTargetType());
        if (plan == null) {
            error(expr, "Can't cast " + value.getAttribute().type + " to " + expr.getTargetType());
            expr.setAttribute(new TypedPlan(new ConstantPlan(null), expr.getTargetType()));
            return;
        }
        expr.setAttribute(plan);
    }

    private TypedPlan tryCast(TypedPlan plan, ValueType targetType) {
        if (plan.getType().equals(targetType)) {
            return plan;
        }

        if (targetType instanceof Primitive) {
            if (!(plan.type instanceof Primitive)) {
                plan = unbox(plan);
                if (plan == null) {
                    return null;
                }
            }
            plan = tryCastPrimitive(plan, (Primitive)targetType);
            if (plan == null) {
                return null;
            }
            return plan;
        }
        if (plan.type instanceof Primitive) {
            plan = box(plan);
            if (plan == null) {
                return null;
            }
        }

        TypeUnifier unifier = createUnifier();
        if (!unifier.unify((GenericType)targetType, (GenericType)plan.type, true)) {
            GenericType erasure = ((GenericType)targetType).erasure();
            plan = new TypedPlan(new CastPlan(plan.plan, typeToString(erasure)),
                    ((GenericType)targetType).substitute(unifier.getSubstitutions()));
        }

        return plan;
    }

    @Override
    public void visit(InstanceOfExpr<TypedPlan> expr) {
        expr.setCheckedType((GenericType)resolveType(expr.getCheckedType(), expr));
        Expr<TypedPlan> value = expr.getValue();
        value.acceptVisitor(this);
        GenericType checkedType = expr.getCheckedType();
        GenericType sourceType = (GenericType)value.getAttribute().type;

        if (!(value.getAttribute().type instanceof GenericClass)) {
            error(expr, "Can't check against " + checkedType + " type");
            expr.setAttribute(new TypedPlan(new ConstantPlan(false), Primitive.BOOLEAN));
            return;
        }

        TypeUnifier unifier = new TypeUnifier(navigator.getClassRepository());
        if (unifier.unify(checkedType, sourceType, true)) {
            expr.setAttribute(new TypedPlan(new ConstantPlan(true), Primitive.BOOLEAN));
        } else {
            GenericType erasure = checkedType.erasure();
            InstanceOfPlan plan = new InstanceOfPlan(value.getAttribute().plan, typeToString(erasure));
            expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
        }
    }

    @Override
    public void visit(InvocationExpr<TypedPlan> expr) {
        expr.getInstance().acceptVisitor(this);
        TypedPlan instance = expr.getInstance().getAttribute();

        if (!(instance.type instanceof GenericClass)) {
            error(expr, "Can't call method of non-class value: " + instance.type);
            expr.setAttribute(new TypedPlan(new ConstantPlan(null), new GenericClass("java.lang.Object")));
            return;
        }

        compileInvocation(expr, instance, (GenericClass)instance.type, expr.getMethodName(), expr.getArguments());
    }

    @Override
    public void visit(StaticInvocationExpr<TypedPlan> expr) {
        compileInvocation(expr, null, navigator.getGenericClass(expr.getClassName()), expr.getMethodName(),
                expr.getArguments());
    }

    private void compileInvocation(Expr<TypedPlan> expr, TypedPlan instance, GenericClass cls, String methodName,
            List<Expr<TypedPlan>> argumentExprList) {
        TypedPlan[] actualArguments = new TypedPlan[argumentExprList.size()];
        for (int i = 0; i < actualArguments.length; ++i) {
            argumentExprList.get(i).acceptVisitor(this);
            actualArguments[i] = argumentExprList.get(i).getAttribute();
        }

        GenericMethod[] methods = navigator.findMethods(cls, methodName, actualArguments.length);
        List<TypedPlan> matchedPlans = new ArrayList<>();
        List<GenericMethod> matchedMethods = new ArrayList<>();
        methods: for (GenericMethod method : methods) {
            if ((instance == null) != method.getDescriber().isStatic()) {
                continue;
            }

            ValueType[] argTypes = method.getActualArgumentTypes();
            Plan[] convertedArguments = new Plan[actualArguments.length];
            TypeUnifier unifier = createUnifier();
            for (int i = 0; i < argTypes.length; ++i) {
                TypedPlan arg = actualArguments[i];
                arg = tryConvert(arg, argTypes[i], unifier);
                if (arg == null) {
                    continue methods;
                }
                convertedArguments[i] = arg.plan;
            }

            ValueType returnType = method.getActualReturnType();
            if (returnType instanceof GenericType) {
                returnType = ((GenericType)returnType).substitute(unifier.getSubstitutions());
            }

            String className = method.getDescriber().getOwner().getName();
            StringBuilder desc = new StringBuilder().append('(');
            for (ValueType argType : method.getDescriber().getRawArgumentTypes()) {
                desc.append(typeToString(argType));
            }
            desc.append(')');
            if (returnType != null) {
                desc.append(typeToString(returnType));
            } else {
                desc.append('V');
            }
            matchedPlans.add(new TypedPlan(new InvocationPlan(className, methodName, desc.toString(),
                    instance != null ? instance.plan : null, convertedArguments), returnType));
            matchedMethods.add(method);
        }

        if (matchedMethods.size() == 1) {
            expr.setAttribute(matchedPlans.get(0));
            return;
        }

        expr.setAttribute(new TypedPlan(new ConstantPlan(null), new GenericReference(nullType)));
        ValueType[] argumentTypes = new ValueType[actualArguments.length];
        for (int i = 0; i < argumentTypes.length; ++i) {
            argumentTypes[i] = actualArguments[i].type;
        }
        if (matchedMethods.isEmpty()) {
            error(expr, "No corresponding method found: " + methodToString(methodName,
                    Arrays.asList(argumentTypes)));
        } else {
            StringBuilder message = new StringBuilder();
            message.append("Call to method ").append(methodToString(methodName,
                    Arrays.asList(argumentTypes))).append(" is ambigous. The following methods match: ");
            for (int i = 0; i < matchedMethods.size(); ++i) {
                if (i > 0) {
                    message.append(", ");
                }
                GenericMethod method = matchedMethods.get(i);
                message.append(methodToString(method.getDescriber().getName(),
                        Arrays.asList(method.getActualArgumentTypes())));
            }
            error(expr, message.toString());
        }
    }

    @Override
    public void visit(PropertyExpr<TypedPlan> expr) {
        expr.getInstance().acceptVisitor(this);
        TypedPlan instance = expr.getInstance().getAttribute();

        if (instance.type instanceof GenericArray && expr.getPropertyName().equals("length")) {
            expr.setAttribute(new TypedPlan(new ArrayLengthPlan(instance.plan), Primitive.INT));
            return;
        }

        if (!(instance.type instanceof GenericClass)) {
            error(expr, "Can't get property of non-class value: " + instance.type);
            expr.setAttribute(new TypedPlan(new ConstantPlan(null), new GenericClass("java.lang.Object")));
            return;
        }

        GenericClass cls = (GenericClass)instance.type;
        compilePropertyAccess(expr, instance, cls, expr.getPropertyName());
    }

    private GenericMethod findGetter(GenericClass cls, String name) {
        GenericMethod method = navigator.getMethod(cls, getGetterName(name));
        if (method == null) {
            method = navigator.getMethod(cls, getBooleanGetterName(name));
            if (method.getActualReturnType() != Primitive.BOOLEAN) {
                method = null;
            }
        }
        return method;
    }

    private String getGetterName(String propertyName) {
        return "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    private String getBooleanGetterName(String propertyName) {
        return "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    @Override
    public void visit(StaticPropertyExpr<TypedPlan> expr) {
        compilePropertyAccess(expr, null, navigator.getGenericClass(expr.getClassName()), expr.getPropertyName());
    }

    private void compilePropertyAccess(Expr<TypedPlan> expr, TypedPlan instance, GenericClass cls,
            String propertyName) {
        GenericField field = navigator.getField(cls, propertyName);
        boolean isStatic = instance == null;
        if (field != null) {
            if (isStatic == field.getDescriber().isStatic()) {
                expr.setAttribute(new TypedPlan(new FieldPlan(instance != null ? instance.plan : null,
                        field.getDescriber().getOwner().getName(), field.getDescriber().getName(),
                        "()" + typeToString(field.getDescriber().getRawType())), field.getActualType()));
                return;
            } else {
                error(expr, "Field " + propertyName + " should " + (!isStatic ? "not " : "") + "be static");
            }
        } else {
            GenericMethod getter = findGetter(cls, propertyName);
            if (getter != null) {
                if (isStatic == getter.getDescriber().isStatic()) {
                    String desc = "()" + typeToString(getter.getDescriber().getRawReturnType());
                    expr.setAttribute(new TypedPlan(new InvocationPlan(getter.getDescriber().getOwner().getName(),
                            getter.getDescriber().getName(), desc, instance != null ? instance.plan : null),
                            getter.getActualReturnType()));
                    return;
                } else {
                    error(expr, "Method " + getter.getDescriber().getName() + " should " +
                            (!isStatic ? "not " : "") + " be static");
                }
            } else {
                error(expr, "Property " + propertyName + " was not found");
            }
        }

        expr.setAttribute(new TypedPlan(new ConstantPlan(null), nullTypeRef));
    }

    @Override
    public void visit(UnaryExpr<TypedPlan> expr) {
        expr.getOperand().acceptVisitor(this);
        switch (expr.getOperation()) {
            case NEGATE: {
                ArithmeticType type = getArithmeticType(expr.getOperand());
                NegatePlan plan = new NegatePlan(expr.getOperand().getAttribute().plan, type);
                expr.setAttribute(new TypedPlan(plan, getType(type)));
                break;
            }
            case NOT: {
                ensureBooleanType(expr.getOperand());
                NotPlan plan = new NotPlan(expr.getOperand().getAttribute().plan);
                expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
                break;
            }
        }
    }

    @Override
    public void visit(VariableExpr<TypedPlan> expr) {
        ValueType type = scope.variableType(expr.getName());
        if (type == null) {
            error(expr, "Variable " + expr.getName() + " was not found");
            type = new GenericClass("java.lang.Object");
        }
        expr.setAttribute(new TypedPlan(new VariablePlan(expr.getName()), type));
    }

    @Override
    public void visit(ConstantExpr<TypedPlan> expr) {
        ValueType type;
        if (expr.getValue() == null) {
            type = new GenericReference(nullType);
        } else if (expr.getValue() instanceof Boolean) {
            type = Primitive.BOOLEAN;
        } else if (expr.getValue() instanceof Character) {
            type = Primitive.CHAR;
        } else if (expr.getValue() instanceof Byte) {
            type = Primitive.BYTE;
        } else if (expr.getValue() instanceof Short) {
            type = Primitive.SHORT;
        } else if (expr.getValue() instanceof Integer) {
            type = Primitive.INT;
        } else if (expr.getValue() instanceof Long) {
            type = Primitive.LONG;
        } else if (expr.getValue() instanceof Float) {
            type = Primitive.FLOAT;
        } else if (expr.getValue() instanceof Double) {
            type = Primitive.DOUBLE;
        } else if (expr.getValue() instanceof String) {
            type = stringClass;
        } else {
            throw new IllegalArgumentException("Don't know how to compile constant: " + expr.getValue());
        }
        expr.setAttribute(new TypedPlan(new ConstantPlan(expr.getValue()), type));
    }

    private void ensureBooleanType(Expr<TypedPlan> expr) {
        convert(expr, Primitive.BOOLEAN);
    }

    private void ensureIntType(Expr<TypedPlan> expr) {
        convert(expr, Primitive.INT);
    }

    private void convertToString(Expr<TypedPlan> expr) {
        if (expr.getAttribute().getType().equals(stringClass)) {
            return;
        }
        ValueType type = expr.getAttribute().type;
        Plan plan = expr.getAttribute().plan;
        if (type instanceof Primitive) {
            GenericClass wrapperClass = primitivesToWrappers.get(type);
            plan = new InvocationPlan(wrapperClass.getName(), "toString", "(" + typeToString(type) +
                    ")Ljava/lang/String;", null, plan);
        } else {
            plan = new InvocationPlan("java.lang.String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;",
                    null, plan);
        }
        expr.setAttribute(new TypedPlan(plan, stringClass));
    }

    private ArithmeticType getArithmeticType(Expr<TypedPlan> expr) {
        TypedPlan plan = expr.getAttribute();
        if (!(plan.getType() instanceof Primitive)) {
            plan = unbox(plan);
        }
        if (plan != null) {
            PrimitiveKind kind = ((Primitive)plan.type).getKind();
            IntegerSubtype subtype = getIntegerSubtype(kind);
            if (subtype != null) {
                expr.setAttribute(new TypedPlan(new CastToIntegerPlan(subtype, plan.plan), Primitive.INT));
                plan = expr.getAttribute();
                kind = ((Primitive)plan.type).getKind();
            }
            ArithmeticType type = getArithmeticType(kind);
            if (type != null) {
                return type;
            }
        }
        error(expr, "Invalid operand type: " + expr.getAttribute().type);
        expr.setAttribute(new TypedPlan(new ConstantPlan(0), Primitive.INT));
        return ArithmeticType.INT;
    }

    private ArithmeticType getAritmeticTypeForPair(Expr<TypedPlan> firstExpr, Expr<TypedPlan> secondExpr) {
        ArithmeticType firstType = getArithmeticType(firstExpr);
        ArithmeticType secondType = getArithmeticType(secondExpr);
        ArithmeticType common = ArithmeticType.values()[Math.max(firstType.ordinal(), secondType.ordinal())];
        if (firstType != common) {
            firstExpr.setAttribute(new TypedPlan(new ArithmeticCastPlan(firstType, common,
                    firstExpr.getAttribute().plan), getType(common)));
        }
        if (secondType != common) {
            secondExpr.setAttribute(new TypedPlan(new ArithmeticCastPlan(secondType, common,
                    secondExpr.getAttribute().plan), getType(common)));
        }
        return common;
    }

    private ValueType getType(ArithmeticType type) {
        switch (type) {
            case DOUBLE:
                return Primitive.DOUBLE;
            case FLOAT:
                return Primitive.FLOAT;
            case INT:
                return Primitive.INT;
            case LONG:
                return Primitive.LONG;
        }
        throw new AssertionError("Unexpected arithmetic type: " + type);
    }

    private ArithmeticType getArithmeticType(PrimitiveKind kind) {
        switch (kind) {
            case INT:
                return ArithmeticType.INT;
            case LONG:
                return ArithmeticType.LONG;
            case FLOAT:
                return ArithmeticType.FLOAT;
            case DOUBLE:
                return ArithmeticType.DOUBLE;
            default:
                return null;
        }
    }

    private IntegerSubtype getIntegerSubtype(PrimitiveKind kind) {
        switch (kind) {
            case BYTE:
                return IntegerSubtype.BYTE;
            case SHORT:
                return IntegerSubtype.SHORT;
            case CHAR:
                return IntegerSubtype.CHAR;
            default:
                return null;
        }
    }

    private BinaryPlanType getPlanType(BinaryOperation op) {
        switch (op) {
            case SUBTRACT:
                return BinaryPlanType.SUBTRACT;
            case MULTIPLY:
                return BinaryPlanType.MULTIPLY;
            case DIVIDE:
                return BinaryPlanType.DIVIDE;
            case EQUAL:
                return BinaryPlanType.EQUAL;
            case NOT_EQUAL:
                return BinaryPlanType.NOT_EQUAL;
            case LESS:
                return BinaryPlanType.LESS;
            case LESS_OR_EQUAL:
                return BinaryPlanType.LESS_OR_EQUAL;
            case GREATER:
                return BinaryPlanType.GREATER;
            case GREATER_OR_EQUAL:
                return BinaryPlanType.GREATER_OR_EQUAL;
            default:
                break;
        }
        throw new AssertionError("Don't know how to map binary operation " + op + " to plan");
    }

    private LogicalBinaryPlanType getLogicalPlanType(BinaryOperation op) {
        switch (op) {
            case AND:
                return LogicalBinaryPlanType.AND;
            case OR:
                return LogicalBinaryPlanType.OR;
            default:
                break;
        }
        throw new AssertionError("Don't know how to map binary operation " + op + " to plan");
    }

    void convert(Expr<TypedPlan> expr, ValueType targetType) {
        TypedPlan plan = expr.getAttribute();
        plan = tryConvert(plan, targetType);
        if (plan != null) {
            expr.setAttribute(plan);
        } else {
            error(expr, "Can't convert " + expr.getAttribute().type + " to " + targetType);
            expr.setAttribute(new TypedPlan(new ConstantPlan(getDefaultConstant(targetType)), targetType));
        }
    }

    TypedPlan tryConvert(TypedPlan plan, ValueType targetType) {
        return tryConvert(plan, targetType, createUnifier());
    }

    TypedPlan tryConvert(TypedPlan plan, ValueType targetType, TypeUnifier unifier) {
        if (plan.getType().equals(targetType)) {
            return plan;
        }
        if (plan.getType().equals(nullTypeRef)) {
            return new TypedPlan(plan.plan, targetType);
        }

        if (targetType instanceof Primitive) {
            if (!(plan.type instanceof Primitive)) {
                plan = unbox(plan);
                if (plan == null) {
                    return null;
                }
            }
            if (!hasImplicitConversion(((Primitive)plan.type).getKind(), ((Primitive)targetType).getKind())) {
                return null;
            }
            plan = tryCastPrimitive(plan, (Primitive)targetType);
            if (plan == null) {
                return null;
            }
            return plan;
        }
        if (plan.type instanceof Primitive) {
            plan = box(plan);
            if (plan == null) {
                return null;
            }
        }

        if (!unifier.unify((GenericType)targetType, (GenericType)plan.type, true)) {
            return null;
        }

        return new TypedPlan(plan.plan, ((GenericType)targetType).substitute(unifier.getSubstitutions()));
    }

    private boolean hasImplicitConversion(PrimitiveKind from, PrimitiveKind to) {
        if (from == to) {
            return true;
        }
        if (from == PrimitiveKind.BOOLEAN || to == PrimitiveKind.BOOLEAN) {
            return false;
        }
        if (from == PrimitiveKind.CHAR) {
            switch (to) {
                case INT:
                case LONG:
                case FLOAT:
                case DOUBLE:
                    return true;
                default:
                    return false;
            }
        } else if (to == PrimitiveKind.CHAR) {
            return from == PrimitiveKind.BYTE;
        } else {
            int a = arithmeticSize(from);
            int b = arithmeticSize(to);
            if (a < 0 || b < 0) {
                return false;
            }
            return a < b;
        }
    }

    private int arithmeticSize(PrimitiveKind kind) {
        switch (kind) {
            case BYTE:
                return 0;
            case SHORT:
                return 1;
            case INT:
                return 2;
            case LONG:
                return 3;
            case FLOAT:
                return 4;
            case DOUBLE:
                return 5;
            default:
                return -1;
        }
    }

    private TypedPlan tryCastPrimitive(TypedPlan plan, Primitive targetType) {
        Primitive sourceType = (Primitive)plan.type;
        if (sourceType == targetType) {
            return plan;
        }
        if (sourceType.getKind() == PrimitiveKind.BOOLEAN) {
            if (targetType != Primitive.BOOLEAN) {
                return null;
            }
        } else {
            IntegerSubtype subtype = getIntegerSubtype(sourceType.getKind());
            if (subtype != null) {
                plan = new TypedPlan(new CastToIntegerPlan(subtype, plan.plan), Primitive.INT);
                sourceType = (Primitive)plan.type;
            }
            ArithmeticType sourceArithmetic = getArithmeticType(sourceType.getKind());
            if (sourceArithmetic == null) {
                return null;
            }
            subtype = getIntegerSubtype(targetType.getKind());
            ArithmeticType targetArithmetic = getArithmeticType(targetType.getKind());
            if (targetArithmetic == null) {
                if (subtype == null) {
                    return null;
                }
                targetArithmetic = ArithmeticType.INT;
            }
            plan = new TypedPlan(new ArithmeticCastPlan(sourceArithmetic, targetArithmetic, plan.plan),
                    getType(targetArithmetic));
            if (subtype != null) {
                plan = new TypedPlan(new CastFromIntegerPlan(subtype, plan.plan), targetType);
            }
        }
        return plan;
    }

    private TypedPlan unbox(TypedPlan plan) {
        if (!(plan.type instanceof GenericClass)) {
            return null;
        }
        GenericClass wrapper = (GenericClass)plan.type;
        Primitive primitive = wrappersToPrimitives.get(wrapper);
        if (primitive == null) {
            return null;
        }
        String methodName = primitive.getKind().name().toLowerCase() + "Value";
        return new TypedPlan(new InvocationPlan(wrapper.getName(), methodName, "()" + typeToString(primitive),
                plan.plan), primitive);
    }

    private TypedPlan box(TypedPlan plan) {
        if (!(plan.type instanceof Primitive)) {
            return null;
        }
        GenericClass wrapper = primitivesToWrappers.get(plan.type);
        if (wrapper == null) {
            return null;
        }
        return new TypedPlan(new InvocationPlan(wrapper.getName(), "valueOf", "(" + typeToString(plan.type) +
                ")" + typeToString(wrapper), null, plan.plan), wrapper);
    }

    private Object getDefaultConstant(ValueType type) {
        if (type instanceof Primitive) {
            switch (((Primitive)type).getKind()) {
                case BOOLEAN:
                    return false;
                case CHAR:
                    return '\0';
                case BYTE:
                    return (byte)0;
                case SHORT:
                    return (short)0;
                case INT:
                    return 0;
                case LONG:
                    return 0L;
                case FLOAT:
                    return 0F;
                case DOUBLE:
                    return 0.0;
            }
        }
        return null;
    }

    private TypeUnifier createUnifier() {
        return new TypeUnifier(navigator.getClassRepository());
    }

    private String typeToString(ValueType type) {
        StringBuilder sb = new StringBuilder();
        typeToString(type, sb);
        return sb.toString();
    }

    private void typeToString(ValueType type, StringBuilder sb) {
        if (type instanceof Primitive) {
            switch (((Primitive)type).getKind()) {
                case BOOLEAN:
                    sb.append('Z');
                    break;
                case CHAR:
                    sb.append('C');
                    break;
                case BYTE:
                    sb.append('B');
                    break;
                case SHORT:
                    sb.append('S');
                    break;
                case INT:
                    sb.append('I');
                    break;
                case LONG:
                    sb.append('J');
                    break;
                case FLOAT:
                    sb.append('F');
                    break;
                case DOUBLE:
                    sb.append('D');
                    break;
            }
        } else if (type instanceof GenericArray) {
            sb.append('[');
            typeToString(((GenericArray)type).getElementType(), sb);
        } else if (type instanceof GenericClass) {
            sb.append('L').append(((GenericClass)type).getName().replace('.', '/')).append(';');
        }
    }

    private ValueType resolveType(ValueType type, Expr<TypedPlan> expr) {
        if (type instanceof GenericClass) {
            GenericClass cls = (GenericClass)type;
            String resolvedName = classResolver.findClass(cls.getName());
            if (resolvedName == null) {
                error(expr, "Class not found: " + cls.getName());
                return type;
            }
            boolean changed = !resolvedName.equals(cls.getName());
            List<GenericType> arguments = new ArrayList<>();
            for (GenericType arg : cls.getArguments()) {
                GenericType resolvedArg = (GenericType)resolveType(arg, expr);
                if (resolvedArg != arg) {
                    changed = true;
                }
            }
            return !changed ? type : new GenericClass(resolvedName, arguments);
        } else if (type instanceof GenericArray) {
            GenericArray array = (GenericArray)type;
            ValueType elementType = resolveType(array.getElementType(), expr);
            return elementType == array.getElementType() ? type : new GenericArray(elementType);
        } else {
            return type;
        }
    }

    private String methodToString(String name, List<ValueType> arguments) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('(');
        ValueTypeFormatter formatter = new ValueTypeFormatter();
        for (int i = 0; i < arguments.size(); ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            formatter.format(arguments.get(i), sb);
        }
        sb.append(")");
        return sb.toString();
    }

    private void error(Expr<TypedPlan> expr, String message) {
        diagnostics.add(new Diagnostic(expr.getStart(), expr.getEnd(), message));
    }
}
