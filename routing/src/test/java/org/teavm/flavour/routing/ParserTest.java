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

import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Alexey Andreev
 */
public class ParserTest {
    @Test
    public void parses() {
        class UsersApp implements UsersRoute {
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

    @PathSet
    static interface UsersRoute extends Route {
        @Path("users")
        void users();

        @Path("users/{id}")
        void viewUser(@PathParameter("id") int id);

        @Path("users/{id}/edit")
        void editUser(@PathParameter("id") int id);
    }
}
