/*
 *  Copyright 2017 Alexey Andreev.
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
package org.teavm.flavour.example.api;

import java.util.List;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.teavm.flavour.rest.Resource;

@Resource
@Path("orders")
public interface OrderFacade {
    @POST
    @Consumes("application/json")
    @Produces("application/json")
    int create(OrderEditDTO data);

    @GET
    @Produces("application/json")
    List<OrderDTO> list(@BeanParam OrderQueryDTO query, @BeanParam QueryPageDTO page);

    @GET
    @Produces("application/json")
    @Path("count")
    int count(@BeanParam OrderQueryDTO query);

    @GET
    @Path("{id}")
    @Produces("application/json")
    OrderDTO get(@PathParam("id") int id);

    @PUT
    @Path("{id}")
    @Consumes("application/json")
    void update(@PathParam("id") int id, OrderEditDTO data);
}
