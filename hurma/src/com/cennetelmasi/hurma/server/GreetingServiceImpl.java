package com.cennetelmasi.hurma.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import net.percederberg.mibble.MibLoaderException;

import com.cennetelmasi.hurma.client.GreetingService;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

/**
 * The server side implementation of the RPC service.
 * @param <NodeObject>
 */
@SuppressWarnings("serial")
public class GreetingServiceImpl<NodeObject> extends RemoteServiceServlet implements
                GreetingService {
	
	private HttpSession session;
	private SimulationEngine se = new SimulationEngine();
	int id = 0;
	
    public String greetServer(String input, String pass) {
    	input = escapeHtml(input);
        if(input.equals("Hurma") && pass.equals("hurma"))
        	return "true";
        else
        	return "false";
    }
    
    private String escapeHtml(String html) {
		if (html == null)
			return null;
		return html.replaceAll("&", "&amp;").replaceAll("<", "&lt;")
				.replaceAll(">", "&gt;");
	}
    
    /**
     * Return format:
     * numberOfNodeTypes, (id, name, mib) ...
     */
    public ArrayList<String> getNodeTypes() {
		ArrayList<String> values = new ArrayList<String>();
    	File file = new File("nodeTypes.xml");
		NodeTypeParser ntp = new NodeTypeParser();
		ntp.parseDocument(file);
		int numberOfNodeTypes = NodeTypeParser.getNodeTypes().size();
		values.add(Integer.toString(numberOfNodeTypes));
		for(NodeTypeObject nodeType : NodeTypeParser.getNodeTypes()) {
			values.add(Integer.toString(nodeType.getId()));
			values.add(nodeType.getName());
			values.add(nodeType.getMIB());
			values.add(nodeType.getIcon());
		}
		System.out.println("SERVER: xml is parsed.");
		return values;
    }
    
    public ArrayList<String> createNode(String mib) {
    	NodeObj node = null;
		try {
			node = new NodeObj(mib);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (MibLoaderException e) {
			e.printStackTrace();
		}
		
    	se.getNodes().add(node);
    	node.setId(id);
    	System.out.println("SERVER: node created, node id: " + id);
    	id++;
    	return processNode(node);
    }
    
    public ArrayList<String> getNodeObjValuesById(String id) {
    	for(NodeObj n : se.getNodes())
    		if(n.getId() == Integer.parseInt(id))
    			return processNode(n);
    	return null;
    }
    
    /**
     * Return format:
     * id, nodeName, numberOfDevices, ip, image
     * numberofAlarms, (AlarmName, AlarmOID, AlarmProb, AlarmFreq, isSelected), ... 
     * (ObjectName, ObjectOID, ObjectValue), ...
     */
	public ArrayList<String> processNode(NodeObj node) {
		ArrayList<String> values = new ArrayList<String>();
		values.add(node.getId()+"");
		values.add(node.getNodeName());
		values.add(node.getNumberOfDevices()+"");
		values.add(node.getIp());
		values.add(node.getImage());
		values.add(node.getAlarms().size()+"");
		for(Alarm alarm : node.getAlarms()) {
			values.add(alarm.getName());
			values.add(alarm.getOid());
			values.add(alarm.isSelected()+"");
			values.add(Float.toString(alarm.getProb()));
			values.add(alarm.getFreq()+"");
		}
		for(Alarm alarm : node.getAlarms()) {
			for(Object requiredObject : alarm.getRequiredObjects()) {
				MIBObject obj = node.getMibObjectByOid(requiredObject.toString());
				if(!values.contains(obj.getName()) && obj.isSendable()){
					values.add(obj.getName());
					values.add(obj.getOid().toString());
					values.add(obj.getValue());
				}
			}
		}
		System.out.println("SERVER: node processed, node id: " + node.getId());
		return values;
	}
    
    /**
     * Argument format:
     * values: id, nodeName, numberOfDevices, ip, image
     * selectedAlarms: (oid, probability, frequency), ...
     * requiredFields: (oid, value), ... 
     */
	public void setNodeObjValues(ArrayList<String> values,
			ArrayList<String> selectedAlarms, ArrayList<String> requiredFields) {
		
		//System.out.println("values size: " + values.size() + ", selectedAlarms size: " + selectedAlarms.size() + ", requiredFields size: " + requiredFields.size());
		
		for(NodeObj n : se.getNodes()) {
			if(n.getId() == Integer.parseInt(values.get(0))) {
				// Set values
				n.setNodeName(values.get(1));
				n.setNumberOfDevices(Integer.parseInt(values.get(2)));
				n.setIp(values.get(3));
				n.setImage(values.get(4));
				// Rearrange alarm list
				int size = selectedAlarms.size();
				for(int i=0; i<size;) {
					Alarm alarm = n.getAlarmByOid(selectedAlarms.get(i++));
					alarm.setProb(Float.parseFloat(selectedAlarms.get(i++)));
					alarm.setFreq(Integer.parseInt(selectedAlarms.get(i++)));
					alarm.setSelectStatus(true);
				}
				// Set MIBObject values
				size = requiredFields.size();
				for(int i=0; i<size; i++) {
					MIBObject obj = n.getMibObjectByOid(requiredFields.get(i++));
					if(obj.isSendable())
						obj.setValue(requiredFields.get(i));
				}
				System.out.println("SERVER: node values are set, node id: " + n.getId());
			}
		}
	}
	
	public void deleteNodeObj(String id) {
		int i;
		for(i=0; i<se.getNodes().size(); i++)
			if(se.getNodes().get(i).getId()==Integer.parseInt(id))
				break;
		se.getNodes().remove(i);
		System.out.println("SERVER: node deleted, node id: " + i);
	}
	
	public String getOutputs() {
		String str = se.getProtocol().getLog().toString();
		se.getProtocol().setLog(new StringBuffer());
		return str;
	}

	public void startSimulation(int time, int cofactor) {
		se.start(time, cofactor);
	}

	public String pause() {
		se.pause();
		return getOutputs();
	}

	public void resume() {
		se.resume();
	}

	public String stop() {
		se.stop();
		return getOutputs();
	}

	@Override
	public boolean sessionControl() {
		HttpServletRequest request = this.getThreadLocalRequest();
    	session = request.getSession();
		
		if (session.getAttribute("id") == null)
    		return false;
    	else
    		return true;
	}
	
	@Override
	public void destroySession() {
		session.removeAttribute("id");
	}
	
	@Override
	public void createSession() {
		if (session.getAttribute("id") == null)
    		session.setAttribute("id", "user");
	}
	
	/**
	 * Argument format:
	 * simulation name
	 * simulation type
	 * simulation time (hour:minute:second)
	 * 
	 * to create xml use nodes (list of NodeObj) in se (simulationEngine)
	 */
	@Override
	public void saveSimulation(ArrayList<String> values) {
		se.save(values);
		System.out.println("SERVER: saved");
	}
	
	/**
	 * Return format:
	 * name of saved simulation names
	 */
	@Override
	public ArrayList<String> getSavedSimulationName() {
		ArrayList<String> names = new ArrayList<String>();
		
		File f = new File("saved/");
		String[] children = f.list();
			
		for(String child: children)
			names.add(child);
		
		return names;
	}

	/**
	 * Return format:
	 * name, simulationType, duration, numberOfNode, 
	 * (nodeId), ...
	 */
	@Override
	public ArrayList<String> loadSimulation(String simulationName) {
		File file = new File("saved/" + simulationName);
		TopologyParser tp = new TopologyParser();
		tp.parseDocument(file);
		TopologyObject topology = TopologyParser.getTopology();		
		se.setNodes(topology.getNodes());
		ArrayList<String> values = new ArrayList<String>();
		values.add(topology.getName());
		values.add(topology.getSimulationType());
		values.add(topology.getDuration());
		values.add(topology.getNodes().size()+"");
		for(NodeObj node : topology.getNodes()) {
			values.add(node.getId()+"");
		}
		return values;
	}

	@Override
	public void clear() {
		se.clear();
	}

	@Override
	public String getSimulationState() {
		return se.getSimulationState();
	}

	public int getPassedTime() {
		return se.getProtocol().getPassedTime();
	}
}

