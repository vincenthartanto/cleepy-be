package common.dto.request;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

public class SpecificationRequest {
    @QueryParam("search")
    public String search;
    
    @QueryParam("page") @DefaultValue("0")
    public int page;
    
    @QueryParam("size") @DefaultValue("20")
    public int size;
    

    public SpecificationRequest() {}
}