import org.hyperic.hq.hqapi1.ResourceApi
import org.hyperic.hq.hqapi1.types.Response
import org.hyperic.hq.hqapi1.types.ResponseStatus

import org.hyperic.hq.hqapi1.ResourceEdgeApi
import org.hyperic.hq.hqapi1.types.ResourcePrototype
import org.hyperic.hq.hqapi1.types.ResourcePrototypeResponse
import org.hyperic.hq.hqapi1.types.ResourceResponse
import org.hyperic.hq.hqapi1.types.Resource
import org.hyperic.hq.hqapi1.types.ResourceConfig
import org.hyperic.hq.hqapi1.types.ResourceEdge
import org.hyperic.hq.hqapi1.types.ResourceFrom
import org.hyperic.hq.hqapi1.types.ResourceTo
import org.hyperic.hq.hqapi1.types.ResourcesResponse
import org.hyperic.hq.hqapi1.types.StatusResponse;

////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////
class TempestNodeInfo {
    private String role
    private String ip
    private String agentId
    private String vmId

    public TempestNodeInfo(String[] lst) {
        this.role = strip(lst[1])
        this.ip = strip(lst[4])
        this.vmId = strip(lst[5])
        this.agentId = strip(lst[6])
    }

	public static String strip(String str) {
		return str.replaceAll(" ", "").replaceAll("\t", "")
	}


}

List<TempestNodeInfo> readNodesInfo(String fileName) {
    list = new ArrayList<TempestNodeInfo>()
    try{
        // Open the file that is the first 
        // command line parameter
        FileInputStream fstream = new FileInputStream(fileName)
        // Get the object of DataInputStream
        DataInputStream dataIn = new DataInputStream(fstream)
        BufferedReader br = new BufferedReader(new InputStreamReader(dataIn))
        String strLine
        //Read File Line By Line
        while ((strLine = br.readLine()) != null) {
            // Print the content on the console
            String[] strings = strLine.split("[|]")
			if (strings.length == 8) {
	            //System.out.println ("The second is " + strings[6] + ", " + strLine)
                list.add(new TempestNodeInfo(strings))
            }
        }
        //Close the input stream
        dataIn.close()
        fstream.close()
    }catch (Exception e){//Catch exception if any
        System.err.println("Error: " + e.getMessage())
    }
	return list
}

////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////

hardCodedTmpstName = 'HenryTempestInstance'

void checkSuccess(Response r) {
    if (r.getStatus() != ResponseStatus.SUCCESS) {
        System.err.println("Error running command: " + r.getError().getReasonText())
        System.exit(-1)
    }
}

List<Resource> getResourcesWithProtoTypeName(rApi, name) {
    ResourcePrototypeResponse prototypeResponse = rApi.getResourcePrototype(name)
    checkSuccess(prototypeResponse)
    ResourcesResponse resourcesResponse = rApi.getResources(prototypeResponse.getResourcePrototype(), false, false)
    checkSuccess(resourcesResponse)

    return resourcesResponse.getResource()
}


List<Resource> getActiveNodes(rApi, boshAgents) {
    List<Resource> nodes = new ArrayList<Resource>()
    boshAgents.each { agent -> 
        parent = rApi.getParent(agent).getResource()
        //System.out.println "Find node: " + parent.name
        nodes.add(parent)
    }
	return nodes
}

boshNodes = readNodesInfo('node.lst')
println "Bosh reports " + boshNodes.size() + " nodes"

ResourceApi rApi = api.getResourceApi()
tmpstName = 'VMware Tmpst'
tmpstInstances = getResourcesWithProtoTypeName(rApi, tmpstName)
println "Found " + tmpstInstances.size() + " matching tmpst resource"

targetTmpst = null

for (tmpst in tmpstInstances) {
	if (tmpst.name == hardCodedTmpstName) {
		targetTmpst = tmpst
		break
	}
}

if (targetTmpst == null) {
	println "Please manually create tmpst Instance 'HenryTempestInstance'" 
	exit(1)
}
	

boshAgentName = 'bosh-agent'
boshAgentInstances = getResourcesWithProtoTypeName(rApi, boshAgentName)

activeNodes = getActiveNodes(rApi, boshAgentInstances)
//println "Found " + activeNodes.size() + " active node candidates"

ResourceFrom parent = new ResourceFrom()
parent.setResource(targetTmpst)
ResourceTo children = new ResourceTo()
List<Resource> changedNodes = new ArrayList<Resource>()
boshNodes.each { boshNode ->
	for (activeNode in activeNodes) {
		println "Check '" + activeNode.name + "' vs. '" + boshNode.agentId + "'"
		if (activeNode.name.equals(boshNode.agentId) || activeNode.name.equals('cloud_controller/0')) {
			println "Find one match Node"
	        //ConfigResponse cprops = new ConfigResponse()
			//cprops = setValue("originalId", activeNode.name)
			activeNode.setName(boshNode.role)
			activeNode.
            //activeNode.addProperties(cprops)
			//changedNodes.add(activeNode)
			children.getResource().add(activeNode)
		}
	}
}

//checkSuccess(rApi.syncResources(changedNodes))	

List<ResourceEdge> tmpstToVmEdges  = new ArrayList<ResourceEdge>()
ResourceEdge rEdge = new ResourceEdge();
rEdge.setRelation("virtual");
rEdge.setResourceFrom(parent);
rEdge.setResourceTo(children);
tmpstToVmEdges.add(rEdge);	

ResourceEdgeApi reApi = api.getResourceEdgeApi()
StatusResponse syncResponse = reApi.syncResourceEdges(tmpstToVmEdges)
checkSuccess(syncResponse)

