/****************************************************************/
/* Class ModelBuilder                                           */
/* Reads classified data and converts it into an RDF graph      */
/*                                                              */
/* Author: Vivian Silva                                         */
/****************************************************************/

package model;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.function.Predicate;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ReifiedStatement;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.URIref;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class ModelBuilder {
	
	private static Model m;
	private static boolean xml = true;
	private static boolean nt = true;
	private static String resName;
	private static String dsr;
	private static String nsyn;
	private static String vsyn;
	private static String exp;

	private static final Map<String, String> props;
    static {
        Map<String, String> temp = new HashMap<String, String>();
        temp.put("supertype", "has_supertype");
        temp.put("differentia-quality", "has_diff_qual");
        temp.put("differentia-event", "has_diff_event");
        temp.put("event-time", "at_time");
        temp.put("event-location", "at_location");
        temp.put("quality-modifier", "has_qual_modif");
        temp.put("origin-location", "has_origin_loc");
        temp.put("purpose", "has_purpose");
        temp.put("associated-fact", "has_assoc_fact");
        temp.put("accessory-quality", "has_acc_qual");
        temp.put("accessory-determiner", "has_acc_det");
       
        props = Collections.unmodifiableMap(temp);
    }
    
	public ModelBuilder(){
    	
    	String namespace = "";
    	
    	//Disable log messages
    	Logger.getRootLogger().setLevel(Level.OFF);
    	
    	//Create RDF model
    	m = ModelFactory.createDefaultModel();
    	
    	//Read the conf file and set the model parameters
    	try{
	    	BufferedReader br = new BufferedReader(new FileReader("conf/params.txt"));
			
			try{
				String line = null;
					
				while ((line = br.readLine()) != null) {
					if (!line.startsWith("#")){
						if (line.startsWith("namespace = ")){
							namespace = line.substring(line.indexOf('=')+2);
						}
						else if (line.startsWith("resource_name = ")){
							resName = line.substring(line.indexOf('=')+2);
						}
						else if (line.startsWith("XML = ")){
							if (line.endsWith("false")){
								xml = false;
							}		
						}
						else if (line.startsWith("N-TRIPLES = ")){
							if (line.endsWith("false")){
								nt = false;
							}
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
    	} catch (FileNotFoundException f){
			f.printStackTrace();
		}	
		
		//Set namespaces
		if (namespace.charAt(namespace.length()-1) != '/'){
			namespace += "/";
		}
		dsr = namespace + "DefinitionSemanticRoles#";
		nsyn = namespace + "synsets/" + resName + "NounSynset#";
		vsyn = namespace + "synsets/" + resName + "VerbSynset#";
		exp = namespace + "expression/" + resName + "Expression#";
		
		//Make sure at least one format is set to true
		if (!xml && !nt){
			xml = true;
		}
    }
	
	//Read raw data file in the format id|POS|word_list|definition
	private static List<String> loadSynsetList (String inputfile){
		
		List<String> synsets = new ArrayList<String>();
		
    	try{
			BufferedReader br = new BufferedReader(new FileReader(inputfile));
			try{
				String line = null;
								
				while ((line = br.readLine()) != null) {
					synsets.add(line);	
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException f){
			f.printStackTrace();
		}
		
		return synsets;
	}
	
	//Read classified data in IOB format
	@SuppressWarnings("unchecked")
	private static Vector<Vector<String>> loadIOBFile(String inputfile){
		
		Vector<Vector<String>> definitions = new Vector<Vector<String>>();
    	
    	try{
			BufferedReader br = new BufferedReader(new FileReader(inputfile));
			try{
				String line = null;
				Vector<String> def = new Vector<String>();
				
				while ((line = br.readLine()) != null) {
					if (! line.equals("") && !line.equals("BOS O")){
						if (!line.equals("EOS O")){
							def.add(line);
						}
						else{
							definitions.add((Vector<String>)def.clone());
							def.clear();
						}
					}	
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException f){
			f.printStackTrace();
		}
    	
    	return definitions;
	}
	
	//Concatenate all words in a sequence under the same label
	@SuppressWarnings("unchecked")
	private static Vector<Vector<String>> buildSegments(String datafile){
		
		Vector<Vector<String>> segmentedDefs = new Vector<Vector<String>>();
		Vector<Vector<String>> definitions = loadIOBFile(datafile);
		
		for (Vector<String> def : definitions){
			Vector<String> segDef = new Vector<String>();
			Character lastIOB = ' ';
			
			String exp = "";
			String property = "";
			int lines = 0;
		
			for (String line : def){
				String text = line.split(" ")[0];
				String label = line.split(" ")[1];
				Character IOB = label.charAt(0);
				String role = label;
				
				lines++;
				
				if (! IOB.equals('O'))
					role = label.substring(2);
				
				if (IOB.equals('O')){					
					if (lastIOB.equals('B') || lastIOB.equals('I')){
						segDef.add(property + ";" + exp);
						exp = "";
						property = "";
					}
					
					segDef.add(label + ";" + text);
					lastIOB = IOB;
				}
				else{
					if(IOB.equals('B')){						
						if (lastIOB.equals('B') || lastIOB.equals('I')){
							segDef.add(property + ";" + exp);
							exp = "";
							property = "";
						}
						
						if (!role.endsWith("particle"))
							property = props.get(role);
						else
							property = role;
						
						exp = text;
						lastIOB = IOB;
					}
					else{
						if (IOB.equals('I')){		
							exp += " " + text;
							lastIOB = IOB;
						}
					}
					
					if (lines == def.size()){
						segDef.add(property + ";" + exp);
					}
				}
			}
			
			//Look for particles to be appended to their roles
			for (int i=0; i < segDef.size(); i++){
				String role = segDef.get(i).split(";")[0];
				
				if (role.endsWith("particle")){
					String particle = segDef.get(i).split(";")[1];
					String prop = props.get(role.replace("-particle", ""));
					
					boolean appended = false;
					
					//Search for the role backwards
					for (int j=i-1; j >= 0; j--){
						String targetProp = segDef.get(j).split(";")[0];
						
						if (targetProp.equals(prop)){
							String newText = segDef.get(j).split(";")[1] + " " + particle;
							segDef.set(j, prop + ";" + newText);
							segDef.set(i, "");
							appended = true;
							break;
						}
					}
					
					//If still not appended, search forward
					if (!appended){
						for (int j=i+1; j < segDef.size(); j++){
							String targetProp = segDef.get(j).split(";")[0];
							
							if (targetProp.equals(prop)){
								String newText = segDef.get(j).split(";")[1] + " " + particle;
								segDef.set(j, prop + ";" + newText);
								segDef.set(i, "");
								break;
							}
						}
					}
				}
			}
			
			//Clean up the segments list
			Predicate<String> nullString = s -> s.equals("");
			segDef.removeIf(nullString);
			
			segmentedDefs.add((Vector<String>)segDef.clone());
		}
		
		return segmentedDefs;
	}
	
	//Build RDF statements to be added to the model
	private static void buildStatements(String synset, String pos, String spt, Vector<String> def){
		
		boolean qualModif = false;
		String modifier = "";
		
		boolean hasEventComp = false;
		List<String> times = new ArrayList<String>();
		List<String> locs = new ArrayList<String>();
		
		for (String seg : def){
			//Check if there are event components: event times and/or locations
			if (seg.startsWith("at_")){
				hasEventComp = true;
						
				String component = seg.split(";")[1];
				if (seg.startsWith("at_time")) 
					times.add(component); 
				else
					locs.add(component);
			}
		}	
		
		String ns = pos.equals("noun") ? nsyn : vsyn;
		Resource definiendum = m.createResource(ns + URIref.encode(synset));
		
		//Link the definiendum directly to the supertype
		Resource supertype = m.createResource(exp + spt.replaceAll(" ", "_"));
		Property has_supertype = m.createProperty(dsr + "has_supertype");
		m.add(definiendum, has_supertype, supertype);
					
		for (String segment : def){
			//Skip the supertype itself and role components (event times/locations and quality modifiers)
			if (! (segment.startsWith("has_supertype") || segment.startsWith("at_") || segment.startsWith("O"))){
				if (segment.startsWith("has_qual_modif")){
					modifier = segment.split(";")[1];
					qualModif = true;
				}
				else{
					if (segment.startsWith("has_diff_qual") && qualModif){
						//Link the quality modifier to the subsequent differentia quality, then link the
						//reified statement to the supertype
						Resource diff_qual = m.createResource(exp + segment.split(";")[1].replaceAll(" ", "_"));
						Property has_qual_modif = m.createProperty(dsr + "has_qual_modif");
						Property has_diff_qual = m.createProperty(dsr + "has_diff_qual");
									
						Statement st = m.createStatement(diff_qual, has_qual_modif, modifier);
						ReifiedStatement rst = m.createReifiedStatement(st);
									
						Statement sst = m.createStatement(supertype, has_diff_qual, rst);
						ReifiedStatement srst = m.createReifiedStatement(sst);
									
						m.add(definiendum, RDF.type, srst);
									
						modifier = "";
						qualModif = false;
					}
					else{
						if (segment.startsWith("has_diff_event") && hasEventComp){
							//If the event has time(s) and/or location(s), link the differentia event to them,
							//then link the reified statements to the supertype
							Resource diff_event = m.createResource(exp + segment.split(";")[1].replaceAll(" ", "_"));
							Property has_diff_event = m.createProperty(dsr + "has_diff_event");
										
							if (times.size() > 0){
								Property at_time = m.createProperty(dsr + "at_time");
											
								for (String time : times){
									Statement st = m.createStatement(diff_event, at_time, time);
									ReifiedStatement rst = m.createReifiedStatement(st);
												
									Statement sst = m.createStatement(supertype, has_diff_event, rst);
									ReifiedStatement srst = m.createReifiedStatement(sst);
												
									m.add(definiendum, RDF.type, srst);
								}
							}
										
							if (locs.size() > 0){
								Property at_location = m.createProperty(dsr + "at_location");
											
								for (String loc : locs){
									Statement st = m.createStatement(diff_event, at_location, loc);
									ReifiedStatement rst = m.createReifiedStatement(st);
												
									Statement sst = m.createStatement(supertype, has_diff_event, rst);
									ReifiedStatement srst = m.createReifiedStatement(sst);
											
									m.add(definiendum, RDF.type, srst);
								}
							}						
						}
						else{
							//Link the role to the supertype, then link the reified statement to the definiendum
							//and add the new statement to the model
							String prop = segment.split(";")[0];
							String val = segment.split(";")[1];
							
							Property pred = m.createProperty(dsr + prop);
							Statement st = m.createStatement(supertype, pred, val);
							ReifiedStatement rst = m.createReifiedStatement(st);
							
							m.add(definiendum, RDF.type, rst);
						}	
					}	
				}	
			}
		}
	}
	
	//Build a RDF model from classified data
	private static void buildRDFModel(String synsetsfile, String datafile){
		
		m.setNsPrefix("dsr", dsr);
		m.setNsPrefix("nsyn", nsyn);
		m.setNsPrefix("vsyn", vsyn);
		m.setNsPrefix("exp", exp);
		
		List<String> synsets = loadSynsetList(synsetsfile);
		Vector<Vector<String>> definitions = buildSegments(datafile);
		
		//Clean the definitions list, deleting the null roles (with label "O"). Only conjunctions are kept,
		//so the patterns in multi-supertype definitions can be identified
		for (Vector<String> definition : definitions){
			Predicate<String> nullLabel = s -> (s.startsWith("O") && !s.equals("O;or") & !s.equals("O;and"));
			definition.removeIf(nullLabel);
		}
		
		System.out.println("Building model...");
		for (int i=0; i < definitions.size(); i++){
			Vector<String> def = definitions.get(i);
			List<String> supertypes = new ArrayList<String>();
			
			for (String seg : def){
				//Get all supertypes
				if (seg.startsWith("has_supertype")){
					supertypes.add(seg.split(";")[1]);
				}
			}
			
			if (supertypes.size() > 0){
				String pos = synsets.get(i).split("\\|")[1];
				String synset = synsets.get(i).split("\\|")[2].replaceAll(", ", "__");
				
				if (supertypes.size() == 1){
					//Only one supertype; all the other roles (except components) will be linked to it
					buildStatements(synset, pos, supertypes.get(0), def);
				}
				else{
					//More than one supertype, usually separated by the conjunction "or". Depending on the position 
					//of a role, it may be exclusive of one of the supertypes, or shared by all of them
				
					//Get the sequence of roles
					List<String> roles = new ArrayList<String>();
					for (String seg : def){
						if (! (seg.startsWith("O") && (seg.split(";")[1].equals("or") || seg.split(";")[1].equals("and")))){
							roles.add(seg.split(";")[0]);
						}
						else{
							roles.add(seg.split(";")[1]);
						}
					}
					
					boolean leftOnly = false;
					int nonQualSpt = 0;
					
					if (!(roles.get(0).equals("has_supertype") 
							|| (roles.get(0).equals("O") && (roles.get(1).equals("has_supertype"))))){
						//The first role is not a supertype, i.e., the first supertype has a role before it
						int firstSpt = roles.indexOf("has_supertype");
						
						//Check if the other supertypes have any roles before them
						for (int j=firstSpt+1; j < roles.size(); j++){
							if (roles.get(j).equals("has_supertype") && (roles.get(j-1).equals("has_supertype") 
									|| (roles.get(j-1).equals("or") || roles.get(j-1).equals("and") 
											&& roles.get(j-2).equals("has_supertype")))){
								nonQualSpt++;
							}
						}
						
						if (nonQualSpt == supertypes.size()-1){
							leftOnly = true;
						}
					}
					
					if (leftOnly){
						//If all the supertypes but the first one have no roles before it, all the roles will be shared
						for (String supertype : supertypes){
							buildStatements(synset, pos, supertype, def);
						}
					}
					else{	
						Vector<String> newDef = new Vector<String>();
						int currentSpt = 1;
						int lastSpt = roles.lastIndexOf("has_supertype"); 
								
						for (String supertype : supertypes){
							int count = 0;
							int idx = 0;
									
							for (String role : roles){
								if (role.equals("has_supertype")){
									count++;
									if (count == currentSpt){ //Get the index of the current supertype
										idx = def.indexOf("has_supertype;" + supertype);
										break;
									}
								}
							}
							
								
							int backpt = idx-1;
								
							if (backpt > -1 && !(roles.get(backpt).equals("or") || roles.get(backpt).equals("and"))){
								while (backpt > -1 && !(roles.get(backpt).equals("has_supertype"))){
									//Get the roles before this supertype, if any
									newDef.add(def.get(backpt));
									backpt--;
								}
							}	
							
							int forthpt = idx + 1;
							
							if ((roles.get(lastSpt-1).equals("or") || roles.get(lastSpt-1).equals("and"))
									&& !roles.get(lastSpt-2).equals("has_supertype")){
								//Get only the roles after this supertype and before the conjunction introducing the next one
								while (forthpt < roles.size() && !((roles.get(forthpt).equals("or") || roles.get(forthpt).equals("and")) 
										&& roles.get(forthpt+1).equals("has_supertype"))){
									newDef.add(def.get(forthpt));
									forthpt++;
								}
							}
							else{		
								for (int l=lastSpt+1; l < def.size(); l++){
									//Get the roles after the last supertype
									newDef.add(def.get(l));
								}
							}
								
							buildStatements(synset, pos, supertype, newDef);
							newDef.clear();
							currentSpt++;		
						}
					}	
				}	
			}
		}	
		
		try{
			if (xml){
				OutputStream Rwriter = new FileOutputStream("output/" + resName + "_XML.rdf");
				m.write(Rwriter, "RDF/XML-ABBREV");
			}
			
			if (nt){
				OutputStream Nwriter = new FileOutputStream("output/" + resName + "_NTriples.nt");
				m.write(Nwriter, "N-TRIPLES");
			}	
		}
		catch (IOException e){
			e.printStackTrace();
		}
		System.out.println("Model saved in the output folder.");
	}
	
	public static void main (String args[]){
		
		new ModelBuilder();
		
		buildRDFModel("input/definitions.txt", "input/classified.txt");
	}

}

