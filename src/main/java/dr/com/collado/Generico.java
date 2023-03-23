package dr.com.collado;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

public class Generico implements Runnable {
	
	private static Logger log = Logger.getLogger(Generico.class.getSimpleName());
	
	/*PARAMETROS GENERALES*/
	private static String nexusUser = "";
	private static String nexusPass = "";
	private static String nexusUrl 	= "";
	
	private static final String NEXUS_VALUE_REPOSITORY 		= "releases";
	private static final String NEXUS_VALUE_FORMAT     		= "maven2";
	
	private static final String NEXUS_CAMPO_ITEMS   		= "items";
	private static final String NEXUS_CAMPO_ID				= "id";
	private static final String NEXUS_CAMPO_GROUP   		= "group";
	private static final String NEXUS_CAMPO_NAME			= "name";
	private static final String NEXUS_CAMPO_VERSION 		= "version";
	private static final String NEXUS_CAMPO_REPOSITORY 		= "repository";
	private static final String NEXUS_CAMPO_FORMAT 			= "format";
	private static final String NEXUS_CAMPO_MAVEN_GROUP_ID 	= "maven.groupId";
	
	private static final String NEXUS_REST_API_STATUS		= "status";
	private static final String NEXUS_REST_API_SERVICE		= "service";
	private static final String NEXUS_REST_API_REST			= "rest";
	private static final String NEXUS_REST_API_VERSION		= "v1";
	private static final String NEXUS_REST_API_SEARCH		= "search";
	private static final String NEXUS_REST_API_COMPONENTS	= "components";

	String listadoGrupId;
	String pibot;
	String ano;
	String mes;
	String dia;
	String inicio;
	String fin;
	
	public Generico(String listadoGrupId, String pibot, String ano, String mes, String dia, String inicio, String fin) {
		
		this.listadoGrupId = listadoGrupId;
		this.pibot = pibot;
		this.ano = ano;
		this.mes = mes;
		this.dia = dia;
		this.inicio = inicio;
		this.fin = fin;
		
		Thread t = new Thread(this, pibot+"."+ano+mes+dia+"."+inicio);
		t.start();
	}

	private void ejecutar() throws ParseException {
		try {
			
			int i = Integer.valueOf(inicio);
			
			while (i < Integer.valueOf(fin)) {
				String version = pibot+"."+ano+mes+dia+"."+i;
				final WebResource service = getService();
				log.info("GET Verificando el estatus de Nexus");
				final String nexusStatus = service.path(NEXUS_REST_API_SERVICE)
						.path(NEXUS_REST_API_REST)
						.path(NEXUS_REST_API_VERSION)
						.path(NEXUS_REST_API_STATUS)
						.accept(MediaType.APPLICATION_JSON)
						.get(ClientResponse.class).toString();
				log.info(nexusStatus + "\n");
				
				List<String> grouposId = Splitter.on(';')
						  .trimResults()
						  .omitEmptyStrings()
						  .splitToList(listadoGrupId);
				
				for (String groupid : grouposId) {
					eliminarComponente(buscarNexusRepositorio(service,groupid,version), groupid, version, service);
				}
				i++;
			}
			


		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
    private static String buscarNexusRepositorio(WebResource service, String groupid, String version) {
		String dato = service.path(NEXUS_REST_API_SERVICE).path(NEXUS_REST_API_REST).path(NEXUS_REST_API_VERSION).path(NEXUS_REST_API_SEARCH)
				.queryParam(NEXUS_CAMPO_REPOSITORY, NEXUS_VALUE_REPOSITORY)
				.queryParam(NEXUS_CAMPO_FORMAT, NEXUS_VALUE_FORMAT)
				.queryParam(NEXUS_CAMPO_MAVEN_GROUP_ID, groupid)
				.queryParam(NEXUS_CAMPO_VERSION, version)
				.accept(MediaType.APPLICATION_JSON).get(String.class); 
    	
    	return dato;
	}

	private static void eliminarComponente(String repo, String groupid, String version, WebResource service) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode jsonNode = objectMapper.readTree(repo);
		JsonNode jsonNodeItem = jsonNode.get(NEXUS_CAMPO_ITEMS);
		
		for (JsonNode jsn : jsonNodeItem) {
			if(jsn.get(NEXUS_CAMPO_GROUP).toString().contains(groupid)) {
				
				log.info(" id: "+jsn.get(NEXUS_CAMPO_ID)+
						" group: "+jsn.get(NEXUS_CAMPO_GROUP)+
						" name: "+jsn.get(NEXUS_CAMPO_NAME)+
						" version: "+jsn.get(NEXUS_CAMPO_VERSION));
				
				String id = jsn.get(NEXUS_CAMPO_ID).asText();
				final String statusCode = service.path(NEXUS_REST_API_SERVICE)
						.path(NEXUS_REST_API_REST)
						.path(NEXUS_REST_API_VERSION)
						.path(NEXUS_REST_API_COMPONENTS)
						.path(id).accept(MediaType.APPLICATION_JSON)
						.delete(ClientResponse.class).toString();
				log.warning(" version "+jsn.get(NEXUS_CAMPO_VERSION).asText()+" --- "+statusCode);
			}
		}
	}

	private static WebResource getService() {
		ClientConfig config = new DefaultClientConfig();
		Client client = Client.create(config);
		client.addFilter(new HTTPBasicAuthFilter(nexusUser, nexusPass));
		return client.resource(getBaseURI());
	}
    
    private static URI getBaseURI() {
		return UriBuilder.fromUri(nexusUrl).build();
	}
    
    public void run() {
    	try {
			ejecutar();
		} catch (ParseException e) {
			e.printStackTrace();
		}
    }
    
}