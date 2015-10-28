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
package org.teavm.flavour.example.api;

import java.util.List;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

/**
 *
 * @author Alexey Andreev
 */
@Path("products")
public interface ProductFacade {
    @POST
    @Consumes("application/json")
    @Produces("application/json")
    int create(ProductDTO data);

    @GET
    @Produces("application/json")
    List<ProductDTO> list(@BeanParam ProductQueryDTO query);

    @GET
    @Produces("application/json")
    int count(ProductQueryDTO query);

    @GET
    @Path("{id}")
    @Produces("application/json")
    ProductDTO get(@PathParam("id") int id);

    @PUT
    @Path("{id}")
    @Consumes("application/json")
    void update(@PathParam("id") int id, ProductDTO data);

    @DELETE
    @Path("{id}")
    void delete(@PathParam("id") int id);
}
