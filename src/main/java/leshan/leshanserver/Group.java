package leshan.leshanserver;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Endpoint;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteResponse;
import org.eclipse.leshan.server.registration.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Group extends BaseInstanceEnabler{
	
	private static final Logger LOG = LoggerFactory.getLogger(Group.class);
	private static int idCount = 0;
	private int id;
	private String name;
	private ArrayList<Registration> members = new ArrayList<>(); 
	private HashMap<String, String> groupresponse = new HashMap<>();
	
	public Group(){
		this.id=idCount;
		this.name="All Endpoints";
		idCount++;
	}
	@Override
    public ReadResponse read(int resourceid) {
        LOG.info("Read on Device Resource " + resourceid);
        switch (resourceid) {
        case 0:
            return ReadResponse.success(resourceid, id);
        case 1:
            return ReadResponse.success(resourceid, name);
        case 2: {
        	HashMap<String, String> hm = new HashMap<>();
        	for(Registration r : members)
        		hm.put(r.getEndpoint(), r.getId());
        	return ReadResponse.success(resourceid, hm.toString());
        }
        case 6: 
        	return ReadResponse.success(resourceid, groupresponse.toString());         
        default:
            return super.read(resourceid);
        }
    }
	@Override
    public WriteResponse write(int resourceid, LwM2mResource value) {
        LOG.info("Write on Device Resource " + resourceid + " value " + value);
        switch (resourceid) {
        case 1: {
        	this.name = value.getValue().toString();
            return WriteResponse.success();
        }
        
        default:
            return super.write(resourceid, value);
        }
    }
	@Override
    public ExecuteResponse execute(int resourceid, String params) {
        LOG.info("Execute on Device resource " + resourceid);
        
        if (params != null && params.length() != 0) {
            System.out.println("\t params " + params);
            
            switch(resourceid) {
	            case 3: {
	            	ArrayList<Registration> rl = (ArrayList<Registration>) App.getEndpoints();
	            	for(Registration r:rl)
	            		if(r.getId().equals(params))
	            			members.add(r);
	            		
	            	return ExecuteResponse.success(); 
	            }
	            case 4: {
	            	for(Registration r:members)
	            		if(r.getId().equals(params))
	            			members.remove(r);
	            	return ExecuteResponse.success();
	            }
	            case 5: {
	            	try {
						sendGroupRequest(params);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	            	return ExecuteResponse.success();
	            }	
	            default:
	                return super.execute(resourceid, params);
	            }
        }
        return ExecuteResponse.success(); 
    }
	
	public void setMembers(ArrayList<Registration> rl){
		members = rl;
	}
	
	private void sendGroupRequest(String params) throws InterruptedException{
		if(!members.isEmpty() && !params.isEmpty()) {
			String[] args = params.split(" ");
			// Define options for command line tools
			Options options = new Options();
			options.addOption("rt", true, "Request Type, e.g. GET, POST, PUT, DELETE");
			options.addOption("up", true, "Path to LWM2M resource, e.g, 3303/0/5700");
			// Parse arguments
	        CommandLine cl;
	        try {
	            cl = new DefaultParser().parse(options, args);
	        } catch (ParseException e) {
	            System.err.println("Parsing failed.  Reason: " + e.getMessage());
	            return;
	        }
	        // Abort if unexpected options
	        if (cl.getArgs().length > 0) {
	            System.err.println("Unexpected option or arguments : " + cl.getArgList());
	            return;
	        }	        
	        //request type
	        String rtype = "";
	        if(cl.hasOption("rt"))
	        	rtype = cl.getOptionValue("rt");  
	        //uri-path
	        String uripath = "";
	        if(cl.hasOption("up"))
	        	uripath = cl.getOptionValue("up");   
	        System.out.println("Request type: "+rtype+" uri-path: "+uripath);	        
	        if(!rtype.isEmpty()&&!uripath.isEmpty()) {	        
				groupresponse = new HashMap<>();
	    		for(Registration r : members)
	    			if(App.getEndpoints().contains(r)){
	    				Request request;
	    				switch(rtype.toLowerCase()) {
	    					case "post": {request = new Request(Code.POST); break; }
	    					case "put": {request = new Request(Code.PUT); break; }
	    					case "delete": {request = new Request(Code.DELETE); break; }
	    					default: { request = new Request(Code.GET); break; }
	    				}	    				
	        			request.setURI("coap://"+r.getAddress().getHostAddress()+":"+r.getPort()+"/"+uripath);
	        			System.out.println(request.toString());
	        			request.getOptions().setAccept(MediaTypeRegistry.TEXT_PLAIN);
	        			Endpoint endpoint = App.getServer().getCoapServer().getEndpoints().get(App.getServer().getCoapServer().getEndpoints().size()-1);
	        			request.send(endpoint);
	        			Response response = request.waitForResponse();
	        			System.out.println("Response: "+response.toString());
	        			if(!response.getPayloadString().isEmpty())
	        				groupresponse.put(r.getEndpoint(), response.getPayloadString());
	        			else
	        				groupresponse.put(r.getEndpoint(), "No Payload.");
	    			}
	    		fireResourcesChange(6);
	        }
		}
	}
	public ArrayList<Registration> getMembers() {
		return members;
	}
	public int getId() {
		return this.id;
	}
}
