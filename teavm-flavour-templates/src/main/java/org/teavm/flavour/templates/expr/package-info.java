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
/**
 * <p>This package contains classes necessary to parse, compile and evaluate expressions written on
 * Flavour Expression Language (FEL). FEL is mostly used in Flavour templates.
 * Unlike most other expression language, this language is statically typed, that allows to verify
 * templates in compile time.</p>
 *
 * <p>Here are several examples of Flavour expressions:</p>
 *
 * <ul>
 *   <li><code>23</code> &ndash; evaluates the number constant;</li>
 *   <li><code>x + 2</code> &ndash; evaluates sum;</li>
 *   <li><code>foo(x)</code> &ndash; calls method of current context, shorthand for <code>this.foo(x)</code>;</li>
 *   <li><code>v.bar</code> &ndash; either gets value of <code>bar</code> field if it exists and is public, or
 *      calls <code>v.getBar()</code> method;</li>
 * </ul>
 *
 *
 * <h2>Using</h2>
 *
 * <p>Flavour expression passes the following phases:</p>
 *
 * <ul>
 *   <li><strong>Parsing</strong> that gives <em>AST</em> (see {@link org.teavm.flavour.templates.expr.ast}).
 *     The {@link org.teavm.flavour.templates.expr.Parser} class is responsible for this phase.</li>
 *   <li><strong>Compilation</strong> that gives <em>evaluation plan</em>
 *     (see {@link org.teavm.flavour.templates.expr.plan}). To compile expression from AST, use
 *     {@link org.teavm.flavour.templates.expr.Compiler} class.</li>
 *   <li><strong>Evaluation</strong> that computes result of the expression according to evaluation plan.
 *     There are several ways of performing evaluation. The easiest one is to interpret plan using
 *     {@link Interpreter}</li>
 * </ul>
 *
 *
 * <h2>Full syntax</h2>
 *
 */
package org.teavm.flavour.templates.expr;