/****************************************************************/
/* Class RoleExtractor                                          */
/* Reads a list of natural language definitions and identifies  */ 
/* the definition's semantic roles for each of them             */
/*                                                              */
/* Author: Vivian Silva                                         */
/****************************************************************/

package extraction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import edu.stanford.nlp.trees.Tree;

public class RoleExtractor {
	
	private static DefinitionParser dp;
	
	public RoleExtractor(){
		
		try{
			dp = new DefinitionParser();
		}
		catch (IOException e){
			e.printStackTrace();
		}
	}
		
	//Extract the semantic roles from a definition
    @SuppressWarnings("unused")
	private static List<String> classifyDefinition(String definition, String pos){
		
		List<String> roles = new ArrayList<String>();
		
		String expression = new String();
		boolean hasDiffQual = false;
		boolean hasDiffEvent = false;
		
		pos = pos.equals("noun") ? "n" : "v";
		
		//1. Look for an accessory determiner at the beginning of the definition
		String accDet = dp.getAccessoryDeterminer(definition);
		
		if (!accDet.equals("")){
			roles.add(definition.indexOf(accDet.trim()) + "|" + accDet.trim() + "|accessory determiner");
			definition = definition.replaceFirst(accDet.trim(), "");
		}
		
		Tree parseTree = dp.parse(definition).get(0);
		
		List<String> conjunctions = new ArrayList<String>();
		int numConjunctions = 0;
		
		//2. Look for the supertype
		String supertype = new String();
		List<String> firstNP = new ArrayList<String>();
		boolean verbSTfound = false;
		
		//For verbs
		if(pos.equals("v")){
			String head = definition.split(" ")[0];
			expression = "@/VB.?/";
			Tree vbTree = dp.getFirstTreeMatch(expression, parseTree);
			List<String> firstVB = new ArrayList<String>();
			
			String nextWord = new String();
			
			if (vbTree != null){
				firstVB = dp.getWordList(vbTree.yieldWords());
				
				//The first VB must also be the first word in the definition
				if (firstVB.get(0).equals(head)){
					supertype = firstVB.get(0);
					verbSTfound = true;
					
					String newDef = definition;
				
					do{
						//Check if it is a phrasal verb
						expression = "PRT $- (@/VB.?/ < /" + supertype + "/)";
						Tree prtTree = dp.getFirstTreeMatch(expression, parseTree);
						List<String> prt = new ArrayList<String>();
				
						if(prtTree != null){
							prt = dp.getWordList(prtTree.yieldWords());
							supertype += " " + dp.toExpression(prt);
						}
				
						roles.add(definition.indexOf(supertype) + "|" + supertype + "|supertype");

						//Look for additional supertypes separated by conjunctions
						String toReplace = newDef.contains(supertype + " ") ? supertype + " " : supertype;
						newDef = newDef.replaceFirst(Pattern.quote(toReplace), "");
						
						if(newDef.contains(" ")){
							nextWord = newDef.substring(0, newDef.indexOf(" "));
					
							if(nextWord.equals("or") || nextWord.equals("and")){
								roles.add(definition.indexOf(newDef) + "|" + nextWord + "|0");
								supertype = newDef.split(" ")[1];
								newDef = newDef.replaceFirst(Pattern.quote(nextWord) + " ", "");
							}
						}
						else{
							nextWord = "";
						}
					} while(nextWord.equals("or") || nextWord.equals("and"));
				}
			}
		}
		
		//For nouns or misclassified verbs
		if(pos.equals("n") || !verbSTfound){
			expression = "@NP !<< @NP !<- POS  << @/NN.?/ | <- @/NN.?/";
			Tree supertypeTree = dp.getFirstTreeMatch(expression, parseTree);
			
			if (supertypeTree != null){
				firstNP = dp.getWordList(supertypeTree.yieldWords());
					
				//Check whether the NP contains conjunctions
				expression = "@CC";
				List<Tree> conjTrees = dp.getAllTreeMatches(expression, supertypeTree);
			
				if(conjTrees.size() > 0){
					for (Tree conjTree : conjTrees){
						conjunctions.addAll(dp.getWordList(conjTree.yieldWords()));
						numConjunctions = conjunctions.size();
					}
				}
			
				//Strip off leading determiner (the leftmost child of the NP), if any
				expression = "@DT  >>, @NP";
				Tree detTree = dp.getFirstTreeMatch(expression, supertypeTree);
				List<String> determiner = new ArrayList<String>();
			
				if (detTree != null){
					determiner = dp.getWordList(detTree.yieldWords());
				
					firstNP.remove(0);
					roles.add(definition.indexOf(determiner.get(0)) + "|" + determiner.get(0) + "|0");
				}
			}
			
			supertype = dp.getLongestEntry(firstNP, pos, false).replaceAll("_", " ");	
			
			//If the supertype wasn't found (due to parser error), get the first noun in the NP
			boolean skip = false;
			if (supertype.length() == 0){
				if (firstNP.size() > 0){
					expression = "@/NN.?/";
					Tree nounTree = dp.getFirstTreeMatch(expression, supertypeTree);
					List<String> firstNoun = new ArrayList<String>();
				
					if (nounTree != null){
						firstNoun = dp.getWordList(nounTree.yieldWords());
						supertype = firstNoun.get(0);
						skip = true;
					}
					else{//If no noun was found, get the last word
						supertype = firstNP.get(firstNP.size()-1);
					}
				}
				else{//If there's no NP in the sentence, get the rightmost word in the first ROOT's child
					expression = "__ >>- (__ >, ROOT)";
					Tree defaultTree = dp.getFirstTreeMatch(expression, parseTree);
					List<String> defaultST = new ArrayList<String>();
					
					if (defaultTree != null){
						defaultST = dp.getWordList(defaultTree.yieldWords());
						supertype = defaultST.get(0);
					}
				}
			}
			roles.add(definition.indexOf(supertype) + "|" + supertype + "|supertype");
			
			if (skip){
				String temp = dp.toExpression(firstNP).replaceFirst(Pattern.quote(supertype) + " ", "");
				firstNP = dp.toStringList(temp);
			}
			else{
				firstNP = dp.removeAllWords(firstNP, supertype.split(" ").length);
			}	
		
			//If there are conjunctions, check whether any of them are right before a supertype, and, if so,
			//look for additional supertypes
			String lastWord = firstNP.size() > 1 ? firstNP.get(firstNP.size()-1) : supertype;
			int offset = lastWord.equals("or") ? 3 : (lastWord.equals("and") ? 4 : 0);
				
			while(numConjunctions > 0 && (lastWord.equals("or") || lastWord.equals("and"))){
				roles.add(definition.indexOf(supertype)-offset + "|" + lastWord + "|0");
				firstNP = dp.removeAllWords(firstNP, 1);
			
				String addSupertype = dp.getLongestEntry(firstNP, pos, false).replaceAll("_", " ");
			
				roles.add(definition.indexOf(addSupertype) + "|" + addSupertype + "|supertype");
				firstNP = dp.removeAllWords(firstNP, addSupertype.split(" ").length);
				lastWord = firstNP.size() > 1 ? firstNP.get(firstNP.size()-1) : addSupertype;
				numConjunctions--;
			}
		
			//Conjunctions that don't link supertypes don't need to be analyzed
			int pointer = definition.indexOf(dp.toExpression(firstNP));
			for (int i=0; i < numConjunctions; i++){
				if(firstNP.remove("or")){
					roles.add(definition.indexOf("or", pointer) + "|" + "or|0");
					pointer += definition.indexOf("or", pointer);	
				}
				else{
					firstNP.remove("and");
					roles.add(definition.indexOf("and", pointer) + "|" + "and|0");
					pointer += definition.indexOf("and", pointer);
				}
			}
		}
		
		//3. Look for differentia qualities to the left of the supertype
		
		//In the same NP as the supertype
		if (firstNP.size() > 0){
			boolean possessive = false;
			
			while(firstNP.size() > 0){ //TO DO look for accessory qualities
				String diffQual = dp.getLongestEntry(firstNP, "null", true).replaceAll("_", " ");
					
				//Check if it is a POS
				if (diffQual.equals("\'s")){
					possessive = true;
					firstNP = dp.removeAllWords(firstNP, 1);
				}
				else{	
					//Check whether it is not an origin location
					Map<String, Vector<String>> namedEnts = dp.getNamedEntities(diffQual);
					
					if(namedEnts.containsKey("LOCATION")){
						roles.add(definition.indexOf(diffQual) + "|" + diffQual + "|origin location");
					}
					else{
						//Attach a POS previously found, if any
						if (possessive){
							diffQual += "'s";
							possessive = false;
						}
						roles.add(definition.indexOf(diffQual) + "|" + diffQual + "|differentia quality");
					}
						
					firstNP = dp.removeAllWords(firstNP, diffQual.split(" ").length);
					hasDiffQual = true;
				}	
			}
		}
		
		//In an NP without NNs, before the one that contains the supertype
		String supertypeHead = supertype.contains(" ") ? supertype.substring(0, supertype.indexOf(' ')) : supertype;
		expression = "@NP !<< @NP !<< @/NN.?/ $++ (@NP << /" + supertypeHead + "/)";
		List<Tree> leftNPTrees = dp.getAllTreeMatches(expression, parseTree);
		List<String> leftNP = new ArrayList<String>();
		
		if (leftNPTrees.size() > 0){
			for (Tree leftNPTree : leftNPTrees){
				leftNP = dp.getWordList(leftNPTree.yieldWords());
				String diffQual = dp.toExpression(leftNP);
				
				//Check whether it is not an origin location
				Map<String, Vector<String>> namedEnts = dp.getNamedEntities(diffQual);
				
				if(namedEnts.containsKey("LOCATION")){
					roles.add(definition.indexOf(diffQual) + "|" + diffQual + "|origin location");
				}
				else{
					roles.add(definition.indexOf(diffQual) + "|" + diffQual + "|differentia quality");
				}	
				leftNP.clear();
			
				//Checks whether there is a conjunction separating this NP from the next one
				int nextWordPos = definition.indexOf(diffQual) + diffQual.length() + 1;
				
				if (nextWordPos < definition.length() && definition.indexOf(" ", nextWordPos) > nextWordPos){
					String nextWord = definition.substring(nextWordPos, definition.indexOf(" ", nextWordPos));
			
					if (nextWord.equals("or") || nextWord.equals("and")){
						roles.add(nextWordPos + "|" + nextWord + "|0");
					}
				}	
			}	
		}
		
		//4. Look for differentia qualities to the right of the supertype
		
		//For verbs
		boolean verbDQFound = false;
		
		if (pos.equals("v")){
			//PP, NP or VP (under S or not)
			String supertypeTail = supertype.contains(" ") ? supertype.substring(supertype.lastIndexOf(' ')+1, supertype.length()) : supertype;
			String aux = "[$- (PRT << /" + supertypeTail + "/) | $- (@/VB.?/ << /" + supertypeTail + "/)]";
			expression = "@PP " + aux + " | @NP " + aux + " | @VP " + aux + " | S < @VP " + aux;
			Tree diffQualTree = dp.getFirstTreeMatch(expression, parseTree);
			List<String> diffQual = new ArrayList<String>();

			if(diffQualTree != null){
				diffQual = dp.getWordList(diffQualTree.yieldWords());
				verbDQFound = true;	
				
				//Look for a PP complementing the differentia quality
				String lastWord = diffQual.get(diffQual.size()-1);
				expression = "@PP $- (@NP << /" + lastWord + "/) | @PP $- (@PP << /" + lastWord + "/)";
				Tree ppCompTree = dp.getFirstTreeMatch(expression, parseTree);
				
				if(ppCompTree != null){
					diffQual.addAll(dp.getWordList(ppCompTree.yieldWords()));
				}
				
				roles.add(definition.indexOf(dp.toExpression(diffQual)) + "|" + dp.toExpression(diffQual) + "|differentia quality");
			}
			diffQual.clear();
			
			//ADJP or ADVP
			expression = "@ADJP !<< @ADJP | @ADVP !<< @ADVP";
			List<Tree> diffQualTrees = dp.getAllTreeMatches(expression, parseTree);
			
			if(diffQualTrees.size() > 0){
				verbDQFound = true;
				for (Tree DQTree : diffQualTrees){
					diffQual = dp.getWordList(DQTree.yieldWords());
					//Check if there are multiple differentia qualities separated by conjunctions
					while (diffQual.contains("or")){
						String dq = dp.toExpression(diffQual).substring(dp.toExpression(diffQual).lastIndexOf("or")+3, dp.toExpression(diffQual).length());
						roles.add(definition.indexOf(dq) + "|" + dq + "|differentia quality");
						roles.add(definition.indexOf(dq)-3 + "|or|0");
						
						diffQual = dp.removeAllWords(diffQual, dq.split(" ").length+1);
					}
					roles.add(definition.indexOf(dp.toExpression(diffQual)) + "|" + dp.toExpression(diffQual) + "|differentia quality");
					
					//Check if there is a conjunction outside the differentia quality tree
					int DQBegin = definition.indexOf(dp.toExpression(diffQual));
					
					if (DQBegin > 3){
						String previousWord = definition.substring(DQBegin-3, DQBegin-1);
						if (previousWord.equals("or")){
							roles.add(definition.indexOf(dp.toExpression(diffQual))-3 + "|or|0");
						}	
					}
				}	
			}
		}
		
		//For nouns or misclassified verbs
		if(pos.equals("n") || !verbDQFound){
			//In a PP after the NP that contains the supertype
			String supertypeTail = supertype.contains(" ") ? supertype.substring(supertype.lastIndexOf(' ')+1, supertype.length()) : supertype;
			expression = "@PP $- (@NP << /" + supertypeTail + "/)";
			Tree rightDQTree = dp.getFirstTreeMatch(expression, parseTree);
			List<String> rightDiffQual = new ArrayList<String>();
		
			if (rightDQTree != null){
				rightDiffQual = dp.getWordList(rightDQTree.yieldWords());

				//5. Look for a purpose inside the PP
				expression = "@PP <<, for | @VP <<, @TO";
				Tree purpTree = dp.getFirstTreeMatch(expression, rightDQTree);
				List<String> purpose = new ArrayList<String>();
			
				if (purpTree != null){
					purpose = dp.getWordList(purpTree.yieldWords());
					rightDiffQual = dp.removeAllWords(rightDiffQual, purpose.size());
				
					//Check whether the purpose starts with the expression "in order to"
					if(rightDiffQual.size() > 2 && rightDiffQual.get(rightDiffQual.size()-2).equals("in") && rightDiffQual.get(rightDiffQual.size()-1).equals("order")){
						purpose.add(0, "in");
						purpose.add(1, "order");
						rightDiffQual = dp.removeAllWords(rightDiffQual, 2);
					}
					roles.add(definition.indexOf(dp.toExpression(purpose)) + "|" + dp.toExpression(purpose) + "|purpose");
				}
			
				//6. Look for a differentia event inside the PP
				expression = "SBAR";
				Tree sbarTree = dp.getFirstTreeMatch(expression, rightDQTree);			
				List<String> diffEvent = new ArrayList<String>();
			
				if (sbarTree != null){
					diffEvent = dp.getWordList(sbarTree.yieldWords());
				
					if(! dp.toExpression(diffEvent).startsWith("in order to")){
						hasDiffEvent = true;
				
						//7. Look for an associated fact separated by a CC in the SBAR
						expression = "@SBAR $- (@CC << and) | @VP $- (@CC << and) | S $- (@CC << and) < @VP";
						Tree assocFactTree = dp.getFirstTreeMatch(expression, sbarTree);
						List<String> sbarAssocFact = new ArrayList<String>();
					
						if (assocFactTree != null){
							sbarAssocFact = dp.getWordList(assocFactTree.yieldWords());
					
							roles.add(definition.indexOf(dp.toExpression(sbarAssocFact)) + "|" + dp.toExpression(sbarAssocFact) + "|associated fact");
							diffEvent = dp.removeAllWords(diffEvent, sbarAssocFact.size());
					
							String conjunction = diffEvent.get(diffEvent.size()-1);
							int conjIndex = definition.indexOf(conjunction, definition.indexOf(dp.toExpression(diffEvent)));
							roles.add(conjIndex + "|" + conjunction + "|0");
							diffEvent = dp.removeAllWords(diffEvent, 1);
					
							rightDiffQual = dp.removeAllWords(rightDiffQual, sbarAssocFact.size()+1);
						}
					
						Map<String, Vector<String>> namedEnts = dp.getNamedEntities(dp.toExpression(diffEvent));
						int locExpSize = 0;
						int timeExpSize = 0;

						//8. Look for event locations inside the differentia event
						if (namedEnts.containsKey("LOCATION")){
							Vector<String> locations = namedEnts.get("LOCATION");
							String currentLoc = new String();
						
							for (String location: locations){
								expression = "@PP << /" + location + "/";
								Tree ppLocTree = dp.getFirstTreeMatch(expression, sbarTree);						
								List<String> eventLoc = new ArrayList<String>();
							
								if (ppLocTree != null){
									eventLoc = dp.getWordList(ppLocTree.yieldWords());
								
									if(!dp.toExpression(eventLoc).equals(currentLoc)){
										roles.add(definition.indexOf(dp.toExpression(eventLoc)) + "|" + dp.toExpression(eventLoc) + "|event location");
										currentLoc = dp.toExpression(eventLoc);
										locExpSize += eventLoc.size();
									
										String temp = dp.toExpression(diffEvent).replaceFirst(Pattern.quote(dp.toExpression(eventLoc)), "");
										diffEvent = dp.toStringList(temp);
									}
								}	
							}
						}
					
						//9. Look for event times inside the differentia event
						if (namedEnts.containsKey("DATE") || namedEnts.containsKey("TIME")){
							Vector<String> times = namedEnts.containsKey("DATE") ? namedEnts.get("DATE") : namedEnts.get("TIME");
							String currentTime = new String();
						
							for (String time: times){
								expression = "@PP << /" + time + "/";
								Tree ppTimeTree = dp.getFirstTreeMatch(expression, sbarTree);						
								List<String> eventTime = new ArrayList<String>();
							
								if (ppTimeTree != null){
									eventTime = dp.getWordList(ppTimeTree.yieldWords());
								
									if(!dp.toExpression(eventTime).equals(currentTime)){
										roles.add(definition.indexOf(dp.toExpression(eventTime)) + "|" + dp.toExpression(eventTime) + "|event time");
										currentTime = dp.toExpression(eventTime);
										timeExpSize += eventTime.size();
									
										String temp = dp.toExpression(diffEvent).replaceFirst(Pattern.quote(dp.toExpression(eventTime)), "");
										diffEvent = dp.toStringList(temp);
									}
								}	
							}
						}
				
						roles.add(definition.indexOf(dp.toExpression(diffEvent)) + "|" + dp.toExpression(diffEvent) + "|differentia event");
						rightDiffQual = dp.removeAllWords(rightDiffQual, diffEvent.size()+locExpSize+timeExpSize);
					}	
				}
			
				if (rightDiffQual.size() > 0){
					//10. Check if the PP can be classified as an origin location
					Map<String, Vector<String>> namedEnts = dp.getNamedEntities(dp.toExpression(rightDiffQual));
				
					if(namedEnts.containsKey("LOCATION")){
						roles.add(definition.indexOf(dp.toExpression(rightDiffQual)) + "|" + dp.toExpression(rightDiffQual) + "|origin location");
					}
					else{
						//11. Check if the PP can be classified as an even time
						if(namedEnts.containsKey("DATE") || namedEnts.containsKey("TIME")){
							roles.add(definition.indexOf(dp.toExpression(rightDiffQual)) + "|" + dp.toExpression(rightDiffQual) + "|event time");
						}
						else{
							//Only for verbs, look for a PP complementing the differentia quality
							if (pos.equals("v")){
								String lastWord = rightDiffQual.get(rightDiffQual.size()-1);
								expression = "@PP $- (@NP << /" + lastWord + "/) | @PP $- (@PP << /" + lastWord + "/)";
								Tree ppCompTree = dp.getFirstTreeMatch(expression, parseTree);
							
								if(ppCompTree != null){
									rightDiffQual.addAll(dp.getWordList(ppCompTree.yieldWords()));
								}
							}
							roles.add(definition.indexOf(dp.toExpression(rightDiffQual)) + "|" + dp.toExpression(rightDiffQual) + "|differentia quality");
						}
					}
				}	
			}
		
			//12. Look for differentia events (outside a differentia quality PP) -- only for nouns
			expression = "SBAR !>> @PP | @VP !<<, @TO !>>(@VP <<, @TO) !<<, for !>> (@PP <<, for)";
			Tree sbarTree = dp.getFirstTreeMatch(expression, parseTree);		
			List<String> diffEvent = new ArrayList<String>();
			
			if (sbarTree != null && !hasDiffEvent){
				diffEvent = dp.getWordList(sbarTree.yieldWords());
				
				if (!dp.toExpression(diffEvent).startsWith("in order to")){
					hasDiffEvent = true;
					
					//13. Look for an associated fact separated by a CC ("and") in the SBAR
					expression = "@SBAR $- (@CC << and) | @VP $- (@CC << and) | S $- (@CC << and) < @VP";
					Tree assocFactTree = dp.getFirstTreeMatch(expression, sbarTree);				
					List<String> sbarAssocFact = new ArrayList<String>();
					
					if (assocFactTree != null){
						sbarAssocFact = dp.getWordList(assocFactTree.yieldWords());
						
						roles.add(definition.indexOf(dp.toExpression(sbarAssocFact)) + "|" + dp.toExpression(sbarAssocFact) + "|associated fact");
						diffEvent = dp.removeAllWords(diffEvent, sbarAssocFact.size());
						
						String conjunction = diffEvent.get(diffEvent.size()-1);
						int conjIndex = definition.indexOf(conjunction, definition.indexOf(dp.toExpression(diffEvent)));
						roles.add(conjIndex + "|" + conjunction + "|0");
						diffEvent = dp.removeAllWords(diffEvent, 1);
					}
				
					//14. Look for a purpose inside the SBAR
					expression = "@PP <<, for | @VP <<, @TO";
					Tree purpTree = dp.getFirstTreeMatch(expression, sbarTree);
					List<String> sbarPurpose = new ArrayList<String>();
						
					if (purpTree != null){
						sbarPurpose = dp.getWordList(purpTree.yieldWords());
						diffEvent = dp.removeAllWords(diffEvent, sbarPurpose.size());
						
						//Check whether the purpose starts with the expression "in order to"
						if(diffEvent.size() > 2 && diffEvent.get(diffEvent.size()-2).equals("in") && diffEvent.get(diffEvent.size()-1).equals("order")){
							sbarPurpose.add(0, "in");
							sbarPurpose.add(1, "order");
							diffEvent = dp.removeAllWords(diffEvent, 2);
						}
						roles.add(definition.indexOf(dp.toExpression(sbarPurpose)) + "|" + dp.toExpression(sbarPurpose) + "|purpose");
					}
						
					Map<String, Vector<String>> namedEnts = dp.getNamedEntities(dp.toExpression(diffEvent));
					
					//15. Look for event locations inside the differentia event
					if (namedEnts.containsKey("LOCATION")){
						Vector<String> locations = namedEnts.get("LOCATION");
						String currentLoc = new String();
							
						for (String location: locations){
							expression = "@PP << /" + location + "/";
							Tree ppLocTree = dp.getFirstTreeMatch(expression, sbarTree);
							List<String> eventLoc = new ArrayList<String>();
							
							if (ppLocTree != null){
								eventLoc = dp.getWordList(ppLocTree.yieldWords());
									
								if(!dp.toExpression(eventLoc).equals(currentLoc)){
									roles.add(definition.indexOf(dp.toExpression(eventLoc)) + "|" + dp.toExpression(eventLoc) + "|event location");
									currentLoc = dp.toExpression(eventLoc);
										
									String temp = dp.toExpression(diffEvent).replaceFirst(Pattern.quote(dp.toExpression(eventLoc)), "");
									diffEvent = dp.toStringList(temp);
								}
							}	
						}
					}
					
					//16. Look for event times inside the differentia event
					if (namedEnts.containsKey("DATE") || namedEnts.containsKey("TIME")){
						Vector<String> times = namedEnts.containsKey("DATE") ? namedEnts.get("DATE") : namedEnts.get("TIME");
						String currentTime = new String();
						
						for (String time: times){
							expression = "@PP << /" + time + "/";
							Tree ppTimeTree = dp.getFirstTreeMatch(expression, sbarTree);						
							List<String> eventTime = new ArrayList<String>();
							
							if (ppTimeTree != null){
								eventTime = dp.getWordList(ppTimeTree.yieldWords());
								
								if(!dp.toExpression(eventTime).equals(currentTime)){
									roles.add(definition.indexOf(dp.toExpression(eventTime)) + "|" + dp.toExpression(eventTime) + "|event time");
									currentTime = dp.toExpression(eventTime);
									
									String temp = dp.toExpression(diffEvent).replaceFirst(Pattern.quote(dp.toExpression(eventTime)), "");
									diffEvent = dp.toStringList(temp);
								}
							}	
						}
					}	
					roles.add(definition.indexOf(dp.toExpression(diffEvent)) + "|" + dp.toExpression(diffEvent) + "|differentia event");
				}
			}
		}	
		
		//16. Look for a particle
		
		//Phrasal verb particle
		expression = "PRT !$- @/VB.?/";
		Tree prtTree = dp.getFirstTreeMatch(expression, parseTree);
		List<String> prt = new ArrayList<String>();
		
		if(prtTree != null){
			prt = dp.getWordList(prtTree.yieldWords());
			String particle = prt.get(0);
			List<String> dependencies = dp.getDependencies(definition, particle);
			
			for (String dependency : dependencies){
				String relation = dependency.split(";")[1];
				
				if (relation.equals("compound:prt")){
					String governor = dependency.split(";")[0].substring(0, dependency.split(";")[0].indexOf('/'));
					
					if (governor.equals(supertype)){
						roles.add(definition.indexOf(particle) + "|" + particle + "|supertype particle");
						break;
					}
				}
			}
		}
		return roles;
	}
	
	public static void main(String args[]){
		
		new RoleExtractor();
		
		List<String> definitions = dp.loadDataFile("input/definitions.txt");
		List<List<String>> classified = new ArrayList<List<String>>();
		
		System.out.println("Classifying definitions (this may take some time...)");
		for (String def : definitions){
			String[] tokens = def.split("\\|");
			String pos = tokens[1];
			String gloss = tokens[3];

			classified.add(dp.sort(gloss, classifyDefinition(gloss, pos)));
		}
		
		dp.IOBPrint(classified, "output/classified.txt");	
	}
}
