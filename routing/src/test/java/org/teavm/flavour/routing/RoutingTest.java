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
package org.teavm.flavour.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Date;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.jso.core.JSDate;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
@SkipJVM
public class RoutingTest {
    @Test
    public void parses() {
        UsersApp app = new UsersApp();
        assertTrue(app.parse("users"));
        assertEquals(1, app.index);

        app = new UsersApp();
        assertTrue(app.parse("users/23"));
        assertEquals(2, app.index);
        assertEquals(23, app.id);

        app = new UsersApp();
        assertTrue(app.parse("users/42/edit"));
        assertEquals(3, app.index);
        assertEquals(42, app.id);
    }

    @Test
    public void writes() {
        new UsersApp().parse("");

        SavingConsumer consumer = new SavingConsumer();
        UsersRoute route = Routing.build(UsersRoute.class, consumer);

        route.users();
        assertEquals("users", consumer.consumed);

        route.viewUser(23);
        assertEquals("users/23", consumer.consumed);

        route.editUser(42);
        assertEquals("users/42/edit", consumer.consumed);
    }

    static class UsersApp implements UsersRoute {
        int index;
        int id;
        @Override public void users() {
            index = 1;
            id = -1;
        }
        @Override public void viewUser(int id) {
            index = 2;
            this.id = id;
        }
        @Override public void editUser(int id) {
            index = 3;
            this.id = id;
        }
    }

    @Test
    public void parsesDateParam() {
        DateRouteImpl impl = new DateRouteImpl();
        assertTrue(impl.parse("date/2015-10-25"));
        assertEquals("Sun Oct 25 2015", JSDate.create(impl.date.getTime()).toDateString());

        impl = new DateRouteImpl();
        assertFalse(impl.parse("date/foo"));
    }

    @Test
    public void writesDateParam() {
        new DateRouteImpl().parse();

        SavingConsumer consumer = new SavingConsumer();
        DateRoute route = Routing.build(DateRoute.class, consumer);

        route.path(new Date(1445810760000L));
        assertEquals("date/2015-10-25T22:06:00", consumer.consumed);
    }

    static class DateRouteImpl implements DateRoute {
        Date date;
        @Override public void path(Date date) {
            this.date = date;
        }
    }

    @Test
    public void parsesStringParam() {
        StringRouteImpl impl = new StringRouteImpl();
        assertTrue(impl.parse("foo/baz/bar"));
        assertEquals("baz", impl.string);

        impl = new StringRouteImpl();
        assertTrue(impl.parse("foo/bazzz"));
        assertEquals("bazz", impl.string);

        impl = new StringRouteImpl();
        assertTrue(impl.parse("foo/%2F/bar"));
        assertEquals("/", impl.string);
    }

    @Test
    public void writesStringParam() {
        new StringRouteImpl().parse();

        SavingConsumer consumer = new SavingConsumer();
        StringRoute route = Routing.build(StringRoute.class, consumer);

        route.path("baz");
        assertEquals("foo/baz/bar", consumer.consumed);

        route.nondeterminatePath("bazz");
        assertEquals("foo/bazzz", consumer.consumed);

        route.path("/");
        assertEquals("foo/%2F/bar", consumer.consumed);
    }

    static class StringRouteImpl implements StringRoute {
        String string;
        @Override public void path(String text) {
            this.string = text;
        }
        @Override public void nondeterminatePath(String text) {
            this.string = text;
        }
    }

    @Test
    public void parsesEnumParam() {
        class EnumRouteImpl implements EnumRoute {
            TestEnum e;
            @Override public void path(TestEnum param) {
                e = param;
            }
        }

        EnumRouteImpl impl = new EnumRouteImpl();
        assertTrue(impl.parse("prefix-FOO"));
        assertEquals(TestEnum.FOO, impl.e);
    }

    @PathSet
    interface UsersRoute extends Route {
        @Path("users")
        void users();

        @Path("users/{id}")
        void viewUser(@PathParameter("id") int id);

        @Path("users/{id}/edit")
        void editUser(@PathParameter("id") int id);
    }

    @PathSet
    interface DateRoute extends Route {
        @Path("date/{param}")
        void path(@PathParameter("param") Date date);
    }

    @PathSet
    interface StringRoute extends Route {
        @Path("foo/{param}/bar")
        void path(@PathParameter("param") String text);

        @Path("foo/{param}z")
        void nondeterminatePath(@PathParameter("param") String text);
    }

    @PathSet
    interface EnumRoute extends Route {
        @Path("prefix-{param}")
        void path(@PathParameter("param") TestEnum param);
    }

    enum TestEnum {
        FOO,
        BAR
    }

    static class SavingConsumer implements Consumer<String> {
        String consumed;

        @Override
        public void accept(String t) {
            consumed = t;
        }
    }
}
