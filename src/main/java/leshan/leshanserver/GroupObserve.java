package leshan.leshanserver;

import java.net.InetAddress;
import java.nio.channels.MembershipKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.californium.core.coap.Request;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteResponse;
import org.eclipse.leshan.server.registration.Registration;

public class GroupObserve extends BaseInstanceEnabler{
	
	private static int idcount = 0;
	private int id;
	private int groupid;
	private int window;
	private ArrayList<String> resources;
	private HashMap<String, String> groupresponse;
	private ArrayList<Observation> observationList;
	private Timer timer;
	
	public GroupObserve() {
		this.id = idcount;
		this.groupid = 0;
		this.window=0;
		this.resources = new ArrayList<>();
		this.groupresponse = new HashMap<>();
		this.observationList = new ArrayList<>();
		this.timer = new Timer("group observe response");
		idcount ++;
		
	}
	@Override
    public ReadResponse read(int resourceid) {
		switch(resourceid) {
		case 0: return ReadResponse.success(resourceid, id);
		case 1: return ReadResponse.success(resourceid, groupid);
		case 2: return ReadResponse.success(resourceid, resources.toString());
		case 7:  {
			return ReadResponse.success(resourceid, groupresponse.toString());
		}
		case 8: return ReadResponse.success(resourceid, window);
		default:
            return super.read(resourceid);
        }
	}
	@Override
    public WriteResponse write(int resourceid, LwM2mResource value) {
		switch(resourceid) {
			case 1: { groupid = Integer.parseInt(value.getValue().toString()); return WriteResponse.success(); }
			case 8: { window = Integer.parseInt(value.getValue().toString()); return  WriteResponse.success(); }
			default:
	            return super.write(resourceid, value);
        }
	}
	@Override
    public ExecuteResponse execute(int resourceid, String params) {
		if (resourceid == 5 || resourceid == 6 || params != null && params.length() != 0) {
            System.out.println("\t params " + params);
            switch(resourceid) {
            case 3: {
            	if(!resources.contains(params))
            		resources.add(params);
            		break;
            }
            
            case 4: {
            	if(resources.contains(params))
            		resources.remove(params);
            		break;
            } 
            
            case 5: {
            	//start group observe
            	for(String resource : resources) {
            		LwM2mPath target = new LwM2mPath(resource);
            		Group group = App.getGroup(groupid);
            		if(group!=null)
	            		for(Registration registration : group.getMembers()){
	            			System.out.println("Observation Started - Target endpoint: "+registration.getEndpoint()+" Target Resource: "+target.toString());
	            			Observation observation = addAnObservation(registration.getId(), target);
	            			if(observation != null)
	            				observationList.add(observation);
	            		}	
            	}
            	if(window!=0) {
	            	// notify new group observe response	         
	                timer.schedule(new TimerTask() {
	                    @Override
	                    public void run() {
	                        fireResourcesChange(7);
	                    }
	                }, 0, window*1000);
            	}
            	break;
            }
            
            case 6: {
            	//end group observe
            		System.out.println("Ending Active Observations:");
            		
            		for(Observation obsrv : observationList) {
            			System.out.println(obsrv.toString());
            			App.getServer().getObservationService().cancelObservation(obsrv);
            		}
            		observationList.clear();
            		timer.cancel();
            			
            		
            	break;
            }
            default:
                return super.execute(resourceid, params);
            }
	    }
	    return ExecuteResponse.success();     
	}
	
	private Observation addAnObservation(String registrationId, LwM2mPath target) {
        
		Registration registration = App.getServer().getRegistrationService().getById(registrationId);
        ObserveRequest or = new ObserveRequest(target.toString());
        Observation observation = null;
        try {
        	
			ObserveResponse oresp= App.getServer().send(registration, or, 5000);
			observation = oresp.getObservation();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        return observation;
    }
	
	
	public ArrayList<Observation> getObservationList() {
		return this.observationList;
	}
	public void updateResponse(Registration registration, ObserveResponse response) {
		//System.out.println("ObserveResponse from "+registration.getEndpoint()+": "+response.getContent().toString());
		groupresponse.put(registration.getEndpoint()+" "+response.getContent().getId(), response.getContent().toString());
		if(window==0)
			fireResourcesChange(7);
		
	}
	
}
