/****************************************************************/
/* Class IOBtoStandoff                                          */
/* Generate a file in the standoff format to be read by the     */
/* Brat annotation tool                                         */
/*                                                              */
/* Author: Vivian Silva                                         */
/****************************************************************/

package util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class IOBtoStandoff {
	
	//Read data in IOB format
	@SuppressWarnings("unchecked")
	private static Vector<Vector<String>> loadIOBFile(String inputfile){
		
		Vector<Vector<String>> definitions = new Vector<Vector<String>>();
    	
		System.out.println("Reading data file...");
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
	private static Vector<String> buildSegments(Vector<String> iobDef){
		
		Vector<String> segDef = new Vector<String>();
		Character lastIOB = ' ';
		
		String exp = "";
		String firstRole = iobDef.get(0).split(" ")[1];
		String lastRole = firstRole.equals("O") ? firstRole : firstRole.substring(2);
		int lines = 0;
	
		for (String line : iobDef){
			String text = line.split(" ")[0];
			String label = line.split(" ")[1];
			Character IOB = label.charAt(0);
			String role = label;
			
			lines++;
			
			if (! IOB.equals('O'))
				role = label.substring(2);
			
			if (IOB.equals('O')){					
				if (lastIOB.equals('B') || lastIOB.equals('I')){
					segDef.add(exp + ":" + lastRole);
					exp = "";
					lastRole = role;
				}
				
				segDef.add(text + ":" + role);
				lastIOB = IOB;
				lastRole = role;
			}
			else{
				if(IOB.equals('B')){						
					if (lastIOB.equals('B') || lastIOB.equals('I')){
						segDef.add(exp + ":" + lastRole);
						exp = "";
						lastRole = role;
					}
					
					exp = text;
					lastIOB = IOB;
					lastRole = role;
				}
				else{
					if (IOB.equals('I')){		
						exp += " " + text;
						lastIOB = IOB;
					}
				}
				
				if (lines == iobDef.size()){
					segDef.add(exp + ":" + role);
				}
			}
		}
		return segDef;
	}
	
	//Convert to standoff format
	private static void toStandoffFormat(String inputfile, String outputfile){
		
		Vector<Vector<String>> data = loadIOBFile(inputfile);
		List<String> records = new ArrayList<String>();
		int id = 1;
		int pointer = 0;
		
		for(Vector<String> def : data){
			def = buildSegments(def);
			for (String role : def){
				String exp = role.split(":")[0];
				String label = role.split(":")[1];
				int start = pointer;
				int end = pointer + exp.length();
				
				records.add("T" + id + "\t" + label + " " + start + " " + end + "\t" + exp + "\n");
				pointer = end + 1;
				id++;
			}
		}	

		try {
		    FileWriter writer = new FileWriter(outputfile);
		    for (String record: records) {
		        writer.write(record);
		    }
		    writer.flush();
		    writer.close();
		}catch(IOException e){  
			e.printStackTrace();
		}
		System.out.println("Standoff file saved in the output folder.");
	}
	
	public static void main (String args[]){
		
		toStandoffFormat("input/classified.txt", "output/standoff.txt");
	}

}
