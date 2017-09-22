/****************************************************************/
/* Class DefinitionParser                                       */
/* Definition syntactic parsing and syntactic tree handling     */
/*                                                              */
/* Author: Vivian Silva                                         */
/****************************************************************/

package extraction;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.POS;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;

public class DefinitionParser {
	
	private static String wnPath;
	private IDictionary dict;
	
	public DefinitionParser() throws IOException{
		
		//Read the conf file and set general parameters
    	try{
	    	BufferedReader br = new BufferedReader(new FileReader("conf/params.txt"));
			
			try{
				String line = null;
					
				while ((line = br.readLine()) != null) {
					if (!line.startsWith("#")){
						if (line.startsWith("wn_path = ")){
							wnPath = line.substring(line.indexOf('=')+2);
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
    	} catch (FileNotFoundException f){
			f.printStackTrace();
		}
    	
    	//Initialize WordNet dictionary
    	URL url = new URL ("file", null , wnPath);
		dict = new Dictionary(url);
		dict.open();
	}
	
	//Load the plural exceptions list
	private static final Map<String, String> plExceptions;
		static {
        Map<String, String> temp = new HashMap<String, String>();
        
        try{
			BufferedReader br = new BufferedReader(new FileReader("data/pl_exc.txt"));
			try{
				String line = null;
				
				while ((line = br.readLine()) != null) {
					String[] tokens = line.split(" ");
					temp.put(tokens[0], tokens[1]);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException f){
			f.printStackTrace();
		}
        plExceptions = Collections.unmodifiableMap(temp);
    }
	
	//Load the non plural list
	private static final List<String> notPlural;
	    static {
	        List<String> temp = new ArrayList<String>();
	        
	        try{
				BufferedReader br = new BufferedReader(new FileReader("data/no_pl.txt"));
				try{
					String line = null;
					
					while ((line = br.readLine()) != null) {
						temp.add(line);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} catch (FileNotFoundException f){
				f.printStackTrace();
			}
	        notPlural = Collections.unmodifiableList(temp);
	    }

	
	//Load the accessory determiners list
	private static final List<String> acc_det;
    static {
        List<String> temp = new ArrayList<String>();
        
        try{
			BufferedReader br = new BufferedReader(new FileReader("data/acc_det.txt"));
			try{
				String line = null;
				
				while ((line = br.readLine()) != null) {
					temp.add(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException f){
			f.printStackTrace();
		}
        acc_det = Collections.unmodifiableList(temp);
    }
    
    //Read the raw data file in the format id|POS|word_list|definition
    public List<String> loadDataFile(String inputfile){
    	
    	List<String> definitions = new ArrayList<String>();
    	
    	try{
			BufferedReader br = new BufferedReader(new FileReader(inputfile));
			try{
				String line = null;
				
				while ((line = br.readLine()) != null) {
					definitions.add(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException f){
			f.printStackTrace();
		}
    	return definitions;
    }
	
	//Check whether a word is plural
    public boolean isPlural (String word){
		
		if (!notPlural.contains(word.toLowerCase()) && (plExceptions.containsKey(word.toLowerCase()) || word.endsWith("s")))
			return true;
		else
			return false;
	}
	
	//Return the singular form of a word
    public String getSingular (String word){ //stemming rules adapted from http://snowball.tartarus.org/algorithms/english/stemmer.html
		
		String singularForm = new String();
		
		if (isPlural(word) && word.length() > 2){
			if (plExceptions.containsKey(word.toLowerCase())){
				singularForm = plExceptions.get(word.toLowerCase());
			}
			else{
				if (word.endsWith("us") || word.endsWith("ss")){
					singularForm = word;
				}
				else{
					if (word.endsWith("sses")){
						singularForm = word.substring(0, word.lastIndexOf("es"));
					}
					else{
						if (word.endsWith("ies")){
							if (word.length() == 4){
								singularForm = word.substring(0, 3);
							}
							else{
								singularForm = word.substring(0, word.lastIndexOf("ies")) + "y";
							}
						}
						else{
							if (word.endsWith("s")){
								String wordPart = word.substring(0, word.length() - 2);
								if (!wordPart.matches(".*[aeiou].*")){
									singularForm = word;
								}
								else{
									singularForm = word.substring(0, word.length() - 1);
								}
							}
						}
					}
				}
			}
		}
		else{
			singularForm = word;
		}
		
		return singularForm;
	}
    
    //Convert a list of strings to a single string
    public String toExpression (List<String> list){
    	
    	String expression = new String();
    	
    	for(int i=0; i < list.size(); i++){
    		expression += list.get(i);
    		if (i+1 < list.size()){
    			if(!list.get(i+1).equals("\'s")){
    				expression += " ";
    			}	
    		}
    	}
    	
    	return expression;
    }
    
    //Convert an expression to a list of strings
    public List<String> toStringList (String exp){
    	
    	List<String> list = new ArrayList<String>();
    	
    	for(String word: exp.split(" ")){
    		list.add(word);
    	}
    	
    	return list;
    }
	
	//Look for the longest sequence of words in an expression that exists as an entry in WordNet
    @SuppressWarnings("unused")
	public String getLongestEntry(List<String> exp, String pos, boolean anypos){
		
		String longestEntry = new String();
		int idx = 0;
		boolean found = false;
		
		while (idx < exp.size() && !found){
			String entry = new String();
			for (int i=idx; i < exp.size(); i++){
				entry += exp.get(i) + " ";
			}
			entry = entry.trim().replaceAll(" ", "_");
			
			switch (pos){
			case "n":
				IIndexWord words = dict.getIndexWord(getSingular(entry), POS.NOUN);
				try{	
					IWord word = dict.getWord(words.getWordIDs().get(0));
					longestEntry = entry;
					found = true;
				}
				catch (NullPointerException npe){
					idx++;
				}
				break;
			case "v":
				words = dict.getIndexWord(entry, POS.VERB);
				try{
					IWord word = dict.getWord(words.getWordIDs().get(0));
					longestEntry = entry;
					found = true;
				}
				catch (NullPointerException npe){
					idx++;
				}
				break;
			case "a":
				words = dict.getIndexWord(entry, POS.ADJECTIVE);
				try{
					IWord word = dict.getWord(words.getWordIDs().get(0));
					longestEntry = entry;
					found = true;
				}
				catch (NullPointerException npe){
					idx++;
				}
				break;
			case "r":
				words = dict.getIndexWord(entry, POS.ADVERB);
				try{
					IWord word = dict.getWord(words.getWordIDs().get(0));
					longestEntry = entry;
					found = true;
					break;
				}
				catch (NullPointerException npe){
					idx++;
				}
			case "null":
				IIndexWord nouns = dict.getIndexWord(entry, POS.NOUN);
				IIndexWord verbs = dict.getIndexWord(entry, POS.VERB);
				IIndexWord adjs = dict.getIndexWord(entry, POS.ADJECTIVE);
				IIndexWord advs = dict.getIndexWord(entry, POS.ADVERB);
				try{
					IWord word = dict.getWord(nouns.getWordIDs().get(0));
					longestEntry = entry;
					found = true;
					break;
				}
				catch (NullPointerException npen){
					try{
						IWord word = dict.getWord(verbs.getWordIDs().get(0));
						longestEntry = entry;
						found = true;
						break;
					}
					catch (NullPointerException npev){
						try{
							IWord word = dict.getWord(adjs.getWordIDs().get(0));
							longestEntry = entry;
							found = true;
							break;
						}
						catch (NullPointerException npea){
							try{
								IWord word = dict.getWord(advs.getWordIDs().get(0));
								longestEntry = entry;
								found = true;
								break;
							}
							catch (NullPointerException nper){
								if (idx == exp.size()-1){
									longestEntry = entry;
									found = true;
									break;
								}
								else{
									idx++;
								}
							}
						}
					}
				}
			}
		}
		return longestEntry;
	}
	
	//Return the accessory determiner that starts a definition, if any
    public String getAccessoryDeterminer(String sentence){
		
		String accDet = "";
		
		for (int i=0; i < acc_det.size(); i++){
			if (sentence.startsWith(acc_det.get(i))){
				accDet = acc_det.get(i);
				break;
			}
		}
		return accDet;
	}
	
	//Return the syntactic parse tree of a piece of text
    public List<Tree> parse (String text){
	    
		List<Tree> trees = new ArrayList<Tree>();
		
		Properties props = new Properties();
	    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, parse");
	    RedwoodConfiguration.empty().capture(System.err).apply();
	    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
	    RedwoodConfiguration.current().clear().apply();
	    
	    Annotation document = new Annotation(text);
	    pipeline.annotate(document);
	    
	    List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
	    
	    //parse the sentence generating the parse trees
	    for (CoreMap sentence : sentences){
	    	Tree tree = sentence.get(TreeAnnotation.class);
	    	trees.add(tree.deepCopy());
	    }
	    return trees;
	}
    
    //Returns the first subtree of a parse tree that matches a given TRegex expression
    public Tree getFirstTreeMatch (String expression, Tree parseTree){
		
    	Tree match = null;
    	
    	TregexPattern pattern = TregexPattern.compile(expression);
		TregexMatcher matcher = pattern.matcher(parseTree);
	
		if (matcher.findNextMatchingNode()){
			match = matcher.getMatch();
		}
		return match;
    }
    
    //Returns all the subtrees of a parse tree that match a given TRegex expression
    public List<Tree> getAllTreeMatches (String expression, Tree parseTree){
		
    	List<Tree> matches = new ArrayList<Tree>();
    	
    	TregexPattern pattern = TregexPattern.compile(expression);
		TregexMatcher matcher = pattern.matcher(parseTree);
	
		while (matcher.findNextMatchingNode()){
			matches.add(matcher.getMatch().deepCopy());
		}
		return matches;
    }
    
    //Return the named entities in a piece of text
    @SuppressWarnings("unchecked")
	public Map<String, Vector<String>> getNamedEntities (String text){
    	
    	Map<String, Vector<String>> nerMap = new HashMap<String, Vector<String>>();
    	
    	String serializedClassifier = "edu/stanford/nlp/models/ner/english.muc.7class.distsim.crf.ser.gz";
    	RedwoodConfiguration.empty().capture(System.err).apply();
    	CRFClassifier<CoreLabel> classifier = CRFClassifier.getClassifierNoExceptions(serializedClassifier);
    	RedwoodConfiguration.current().clear().apply();
    	List<List<CoreLabel>> classification = classifier.classify(text);
    	
    	for (List<CoreLabel> coreLabels : classification){
    		for (CoreLabel coreLabel : coreLabels){
    			String word = coreLabel.word();
    			String category = coreLabel.get(CoreAnnotations.AnswerAnnotation.class);
    			
    			if(!category.equals("O")){
    				Vector<String> values = new Vector<String>();
    				if (nerMap.containsKey(category)){
    					values = nerMap.get(category);
    				}
    				values.add(word);
					nerMap.put(category, (Vector<String>)values.clone());
					values.clear();
    			}
    		}
    	}
    	return nerMap;
    }
    
    //Return the basic dependencies for a word in a sentence, where this word is the dependent term
    public List<String> getDependencies(String text, String word){
    	
    	List<String> pairs = new ArrayList<String>();
    	
    	Properties props = new Properties();
	    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, depparse");
	    RedwoodConfiguration.empty().capture(System.err).apply();
	    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    	RedwoodConfiguration.current().clear().apply();
	    
	    Annotation document = new Annotation(text);
	    pipeline.annotate(document);
	    
	    List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
	    
	    for (CoreMap sentence : sentences){
	    	SemanticGraph dependencies = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
	    	IndexedWord node = dependencies.getNodeByWordPattern(word);
	    	List<SemanticGraphEdge> edges = dependencies.incomingEdgeList(node);
	    	
	    	for (SemanticGraphEdge edge : edges){
	    		String relation = edge.getRelation().toString();
	    		String governor = edge.getGovernor().toString();
	    		pairs.add(governor + ";" + relation);	
	    	}
	    }
    	return pairs;
    }
	
	//Convert the harvest of a parse tree (word elements at the leaves) to a list of strings
    public List<String> getWordList(List<Word> tree){
		
		List<String> words = new ArrayList<String>();
		
		for(Word word: tree){
			words.add(word.toString());
		}
		
		return words;
	}
    
    //Remove from the expression being analyzed the phrases for which a role has already been identified
    public List<String> removeAllWords(List<String> exp, int numWords){
    	
    	List<String> newExp = new ArrayList<String>();
    	for (int i=0; i < exp.size()-numWords; i++){
    		newExp.add(exp.get(i));
    	}
    	return newExp;
    }
    
    //Sort the roles by their fragments' indexes and fill out missing fragments
    public List<String> sort(String text, List<String> roles){
    	
    	List<String> sorted = new ArrayList<String>();
    	Map<Integer, String> temp = new HashMap<Integer, String>();
    	String[] words = text.split(" ");
    	
    	try{
	    	for (String entry : roles){
	    		int index = Integer.parseInt(entry.split("\\|")[0]);
	    		String fragment = entry.split("\\|")[1];
	    		String role = entry.split("\\|")[2];
	    		
	    		if(! temp.containsKey(index)){
	    			temp.put(index, fragment + ":" + role);
	    		}
	    	}
	    	
	    	String filler = "";
	    	String firstRole = "";
	    	String fragment = "";
	    	
	    	int pointer = 1;
	    	int fragLength = 0;
	    	int nextIndex = 0;
	    	int wordsAdded = 0;
	    	
	    	if (temp.containsKey(0) && temp.get(0).split(":")[0].split(" ").length == words.length && roles.size() > 1){
	    		temp.remove(0);
	    	}
	    	
	    	if (temp.containsKey(0)){
	    		sorted.add(temp.get(0));
	    		
	    		firstRole = temp.get(0).split(":")[1];
	        	fragment = temp.get(0).split(":")[0];
	        	
	        	fragLength = fragment.length();
	        	nextIndex = fragLength + 1;
	        	wordsAdded = fragment.split(" ").length;
	    	}
	    	else{
	    		pointer = 0;
	    		while(!temp.containsKey(nextIndex) && wordsAdded < words.length){
					filler += words[wordsAdded] + " ";
					fragLength = words[wordsAdded].length();
					nextIndex += fragLength + 1;
					wordsAdded++;
					
					if (wordsAdded == words.length){
						pointer = roles.size();
					}
				}
	    		sorted.add(filler.trim() + ":0");
				filler = "";
	    	}
	    	
	    	if (firstRole.equals("accessory determiner")){
	    		if(temp.containsKey(1)){
	    			if (temp.get(1).split(":")[0].split(" ").length == (words.length-wordsAdded) && roles.size() > 2){
	    	    		temp.remove(1);
	    	    		nextIndex = 1;
	    	    	}
	    			else{
			    		sorted.add(temp.get(1));
			    		fragment = temp.get(1).split(":")[0];
			    		fragLength = fragment.length();
			    		wordsAdded += fragment.split(" ").length;
			    		nextIndex = 2 + fragLength;
			    		pointer = 2;
	    			}	
	    		}
	    		else{
	    			nextIndex = 1;
	    		}	
	    	}
	    	
	    	if(!temp.containsKey(0) && pointer == 1 && roles.size() == 1){
	    		pointer = 0;
	    	}
	    	
	    	while (pointer < roles.size()){
	    		if (temp.containsKey(nextIndex)){
	    			sorted.add(temp.get(nextIndex));    				
	    			fragment = temp.get(nextIndex).split(":")[0];
	    			fragLength = fragment.length();
	    			wordsAdded += fragment.split(" ").length;
	    			nextIndex += fragLength + 1;
	    			
	    			if (wordsAdded == words.length){
						pointer = roles.size();
					}
	    			else{
	    				pointer++;
	    			}
	    		}
	    		else{
	    			while(!temp.containsKey(nextIndex) && wordsAdded < words.length){    				
	    				filler += words[wordsAdded] + " ";
	    				fragLength = words[wordsAdded].length();
	    				nextIndex += fragLength + 1;
	    				wordsAdded++;
	    				
	    				if (wordsAdded == words.length){
	    					pointer = roles.size();
	    				}
	    			}
	    			sorted.add(filler.trim() + ":0");
	    			filler = "";
	    		}
	    	}
	    	
	    	if(wordsAdded < words.length){
				for (int i=wordsAdded; i < words.length; i++){
					filler += words[i] + " ";
				}
				sorted.add(filler.trim() + ":0");
				filler = "";
			}
    	}
    	catch (Exception e){
    		//If some error prevented proper sorting, skip definition
    		sorted.add(text + ":0");
    	}
    	return sorted;
    }
    
    //Write the data in IOB format to a text file
    public void IOBPrint(List<List<String>> defs, String outputfile){
    	
    	List<String> records = new ArrayList<String>();
    	
    	for(List<String> def : defs){
    		records.add("BOS O\n");
    		for(String role : def){
    			String[] text = role.split(":")[0].split(" ");
    			String label = role.split(":")[1].replaceAll(" ", "-");
    			
    			if (label.equals("0")){
    				for (int i=0; i < text.length; i++){
    					records.add(text[i] + " O\n");
    				}
    			}
    			else{
    				records.add(text[0] + " B-" + label + "\n");
    				for (int i=1; i < text.length; i++){
    					records.add(text[i] + " I-" + label + "\n"); 
    				}
    			}
    		}
    		records.add("EOS O\n\n");
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
    	System.out.println("Classified definitions saved in the output folder.");
    }

}
