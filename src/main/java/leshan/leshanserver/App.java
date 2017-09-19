package leshan.leshanserver;

import static org.eclipse.leshan.LwM2mId.DEVICE;
import static org.eclipse.leshan.LwM2mId.LOCATION;
import static org.eclipse.leshan.LwM2mId.SECURITY;
import static org.eclipse.leshan.LwM2mId.SERVER;
import static org.eclipse.leshan.client.object.Security.noSec;
import static org.eclipse.leshan.client.object.Security.noSecBootstap;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Option;
import org.eclipse.californium.core.coap.OptionNumberRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.resource.LwM2mInstanceEnabler;
import org.eclipse.leshan.client.resource.LwM2mInstanceEnablerFactory;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.client.servers.DmServerInfo;
import org.eclipse.leshan.client.servers.ServerInfo;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.request.LwM2mRequest;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.server.californium.LeshanServerBuilder;
import org.eclipse.leshan.server.californium.impl.LeshanServer;
import org.eclipse.leshan.server.observation.ObservationListener;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationListener;
import org.eclipse.leshan.server.registration.RegistrationUpdate;
import org.eclipse.leshan.server.request.LwM2mRequestSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http2.Http2Connection.Endpoint;



/**
 * Server
 *
 */
public class App 
{
	
	private static final Logger LOG = LoggerFactory.getLogger(App.class);
	
	private static Group defaultgroup = new Group();
	// build the lwm2m server
	private static final LeshanServer server = new LeshanServerBuilder().build();;
	
	private static  LeshanClient client = null; 
	
	private static ArrayList<Group> groups = new ArrayList<>();
	
	private static ArrayList<GroupObserve> gobservelist = new ArrayList<>();
	
	
    public static void main( String[] args )
    {
    	
        
        server.getRegistrationService().addListener(new RegistrationListener() {
			
			@Override
			public void updated(RegistrationUpdate update, Registration updatedRegistration) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void unregistered(Registration registration, Collection<Observation> observations) {
				System.out.println("Unregistered endpoint: " + registration.getId());
				defaultgroup.setMembers(getEndpoints());
				
				
			}
			
			@Override
			public void registered(Registration registration) {
				System.out.println("New Registration: " + registration.toString());
				defaultgroup.setMembers(getEndpoints());
				
				
			}
		});
        
        server.getObservationService().addListener(new ObservationListener() {
			
			@Override
			public void onResponse(Observation observation, Registration registration, ObserveResponse response) {
				System.out.println("ObserveResponse: "+response.toString());
				GroupObserve gobsrv = null;
				for(GroupObserve go : gobservelist)
					for(Observation obs : go.getObservationList())
						if(obs.getPath().equals(observation.getPath())&&obs.getRegistrationId().equals(registration.getId()))
							gobsrv = go;
				if(gobsrv!=null)
					gobsrv.updateResponse(registration, response);
				
				
				
				
			}
			
			@Override
			public void onError(Observation observation, Registration registration, Exception error) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void newObservation(Observation observation, Registration registration) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void cancelled(Observation observation) {
				// TODO Auto-generated method stub
				
			}
		});
 
        
        //build the lwm2m client
        ObjectsInitializer initializer = new ObjectsInitializer();
        //initializer.setInstancesForObject(SECURITY, noSecBootstap("coap://localhost:5684"));
        initializer.setInstancesForObject(SECURITY, noSec("coap://192.168.1.2:5683", 123));
        initializer.setInstancesForObject(SERVER, new Server(123, 30, BindingMode.U, false));
        initializer.setClassForObject(DEVICE, MyDevice.class);
        initializer.setClassForObject(26242, Group.class); // objecto grupo
        initializer.setClassForObject(26243, GroupObserve.class);
        
        initializer.setInstancesForObject(26242, defaultgroup);
        
		groups.add(defaultgroup);
        
        initializer.setFactoryForObject(26242, new LwM2mInstanceEnablerFactory() {
			
			@Override
			public LwM2mInstanceEnabler create(ObjectModel model) {
				Group group = new Group();
				groups.add(group);
				return group;
			}
		});
        
        initializer.setFactoryForObject(26243, new LwM2mInstanceEnablerFactory() {
			
			@Override
			public LwM2mInstanceEnabler create(ObjectModel model) {
				GroupObserve gobserve = new GroupObserve();
				gobservelist.add(gobserve);
				return gobserve;
			}
		});
        
        List<LwM2mObjectEnabler> enablers = initializer.create(SECURITY, SERVER, 26242, 26243);
        String name = "Gateway";
        try {
        	NetworkInterface network = NetworkInterface.getNetworkInterfaces().nextElement();

    		byte[] mac = network.getHardwareAddress();

    		StringBuilder sb = new StringBuilder();
    		for (int i = 0; i < mac.length; i++) {
    			sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
    		}
    		
			name = "Gateway"+sb.toString();
			
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        // Create client,
        LeshanClientBuilder builder = new LeshanClientBuilder(name);
        //builder.setLocalAddress(null, 0);
        //builder.setLocalSecureAddress(null, 0);
        builder.setObjects(enablers);
        
        client = builder.build();
        
    	
        
        server.start();
        client.start();
        
        
            
	}
    public static ArrayList<Registration> getEndpoints() {
    	Iterator<Registration> i = server.getRegistrationService().getAllRegistrations();
    	ArrayList<Registration> l = new ArrayList<>();
    	while(i.hasNext())
    		l.add(i.next());
    	return l; 
    }
    
    public static LeshanServer getServer() {
    	return server;
    }
	public static Group getGroup(int groupid) {
		for(Group group : groups)
			if(group.getId()==groupid)
				return group;
		return null;
	}
    
    
}

