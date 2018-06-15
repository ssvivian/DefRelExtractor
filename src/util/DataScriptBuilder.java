/****************************************************************/
/* Class DataScriptBuilder                                      */
/* Generates a python script for creating the dataset to be     */
/* sent as input for the RNN model                              */
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

public class DataScriptBuilder {
	
	private static Map<String, Integer> words2idx = new HashMap<String, Integer>();
	private static Map<String, Integer> labels2idx = new HashMap<String, Integer>();
	
	private static Vector<Vector<String>> sentences = new Vector<Vector<String>>();
	private static Vector<Vector<String>> labels = new Vector<Vector<String>>();
	
	private static Vector<Vector<String>> trainSents = new Vector<Vector<String>>();
	private static Vector<Vector<String>> validSents = new Vector<Vector<String>>();
	private static Vector<Vector<String>> testSents = new Vector<Vector<String>>();
	private static Vector<Vector<String>> trainLabels = new Vector<Vector<String>>();
	private static Vector<Vector<String>> validLabels = new Vector<Vector<String>>();
	private static Vector<Vector<String>> testLabels = new Vector<Vector<String>>();
	
	private static final int tableOffset = 170;
	
	@SuppressWarnings("unchecked")
	private static void loadIOBFile (String inputfile){
		
		try{
			BufferedReader br = new BufferedReader(new FileReader(inputfile));
			try{
				String line = null;
				
				Vector<String> sentence = new Vector<String>();
				Vector<String> mapping = new Vector<String>();
				
				while ((line = br.readLine()) != null) {
					
					if (! line.equals("EOS O")){
						if(! line.equals("BOS O") && (! line.equals(""))){
							String word = line.split(" ")[0];
							String label = line.split(" ")[1];
								
							sentence.add(word);
							mapping.add(label);
						}
					}
					else{
						sentences.add((Vector<String>)sentence.clone());
						sentence.clear();
						
						labels.add((Vector<String>)mapping.clone());
						mapping.clear();	
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException f){
			f.printStackTrace();
		}
		
		shuffleSentences();	
	}
	
	private static void shuffleSentences (){
		
		Vector<Vector<String>> newSentences = new Vector<Vector<String>>();
		Vector<Vector<String>> newLabels = new Vector<Vector<String>>();
		Random rand = new Random();
		
		for (int i = 0; i < sentences.size(); i++){
			int index = rand.nextInt(sentences.size());
			newSentences.add(sentences.get(index));
			newLabels.add(labels.get(index));
		}
		sentences.clear();
		sentences = newSentences;
		
		labels.clear();
		labels = newLabels;	
	}
	
	private static void splitData(int train, int valid){
		
		int totalSentences = sentences.size();
		int trainSetSize = Integer.valueOf((int) Math.round(totalSentences * train / 100));
		int validSetSize = Integer.valueOf((int) Math.round(totalSentences * valid / 100));
		
		for (int i=0; i < trainSetSize; i++){
			trainSents.add(sentences.get(i));
			trainLabels.add(labels.get(i));
			
		}
		
		for (int j=trainSetSize; j < trainSetSize+validSetSize; j++){
			validSents.add(sentences.get(j));
			validLabels.add(labels.get(j));
		}
		
		for (int k=trainSetSize+validSetSize; k < sentences.size(); k++){
			testSents.add(sentences.get(k));
			testLabels.add(labels.get(k));
		}
		
		//count word frequencies in train set
		Map<String, Integer> freqs = new HashMap<String, Integer>();
		
		for (Vector<String> sentence : trainSents){
			for (String word : sentence){
				if (freqs.containsKey(word.toLowerCase())){
					int freq = freqs.get(word.toLowerCase()) + 1;
					freqs.put(word.toLowerCase(), freq);
				}
				else{
					freqs.put(word.toLowerCase(), 1);
				}
			}
		}
		
		//load words2idx mappings
		int wordIdx = 0;
		boolean unk = false;
		
		for (Iterator<String> l = freqs.keySet().iterator(); l.hasNext(); ){
			String word = (String) l.next();
			
			if (freqs.get(word) == 1){
				if (!unk){
					words2idx.put("<UNK>", wordIdx);
					wordIdx++;
					unk = true;
				}
			}
			else{
				words2idx.put(word, wordIdx);
				wordIdx++;	
			}
		}
		
		//load labels2idx mappings
		int labelIdx = 0;
		
		for (Vector<String> mappings : trainLabels){
			for (String label : mappings){
				if (! labels2idx.containsKey(label)){
					labels2idx.put(label, labelIdx);
					labelIdx++;
				}
			}
		}
		
	}
	
	private static String buildTuple(Vector<Vector<String>> sentences, Vector<Vector<String>> labels){
		
		String tuple = new String();
		
		String wordTuple = "[";
		String tableTuple = "[";
		String labelTuple = "[";
		
		//build word indexes tuple
		for (Vector<String> sentence : sentences){
			String wordArray = "array([";
					
			for (String word : sentence){
				int wordIndex;
				
				if (words2idx.containsKey(word)){
					wordIndex = words2idx.get(word);
				}
				else{
					wordIndex = words2idx.get("<UNK>");
				}
						
				wordArray = wordArray + String.valueOf(wordIndex) + ", ";
			}
			
			wordArray = wordArray.substring(0, wordArray.lastIndexOf(',')) + "], dtype=int32)";
			wordTuple = wordTuple + wordArray + ",\n ";
		}
		
		wordTuple = wordTuple.substring(0, wordTuple.lastIndexOf(',')) + "]";
		
		//build label and table indexes tuples
		for (Vector<String> mapping : labels){
			String labelArray = "array([";
			String tableArray = "array([";
					
			for (String label : mapping){
				int labelIndex;
				int tableIndex;
				
				if (labels2idx.containsKey(label)){
					labelIndex = labels2idx.get(label);
					tableIndex = labelIndex + tableOffset;
				}
				else{
					labelIndex = -1;
					tableIndex = -1;
					System.out.println("Label not in training set: " + label + ". Please run script builder again.");
					System.exit(0);
				}
			
				labelArray = labelArray + String.valueOf(labelIndex) + ", ";
				tableArray = tableArray + String.valueOf(tableIndex) + ", ";
			}
			
			labelArray = labelArray.substring(0, labelArray.lastIndexOf(',')) + "], dtype=int32)";
			tableArray = tableArray.substring(0, tableArray.lastIndexOf(',')) + "], dtype=int32)";
					
			labelTuple = labelTuple + labelArray + ",\n ";
			tableTuple = tableTuple + tableArray + ",\n ";
		}
		
		labelTuple = labelTuple.substring(0, labelTuple.lastIndexOf(',')) + "]";
		tableTuple = tableTuple.substring(0, tableTuple.lastIndexOf(',')) + "]";
				
		//full tuple
		tuple = "(" + wordTuple + ",\n " + tableTuple + ",\n " + labelTuple + ")";
		
		return tuple;
	}
	
	private static String buildDicts (){
		
		String dicts = new String();
		
		String labelDict = "'labels2idx': {";
		String tableDict = "'tables2idx': {";
		String wordDict = "'words2idx': {";
		
		//labels and tables dictionaries
		for (Iterator<String> i = labels2idx.keySet().iterator(); i.hasNext();){
			String label = (String)i.next();
			int labelIndex = labels2idx.get(label);
			int tableIndex = labelIndex + tableOffset;
			
			if (labelDict.equals("'labels2idx': {")){
				labelDict = labelDict + "'" + label + "': " + String.valueOf(labelIndex);
				tableDict = tableDict + "'" + label + "': " + String.valueOf(tableIndex);
			}
			else{
				labelDict = labelDict + ", '" + label + "': " + String.valueOf(labelIndex);
				tableDict = tableDict + ", '" + label + "': " + String.valueOf(tableIndex);
			}
		}
		labelDict = labelDict + "}";
		tableDict = tableDict.replaceFirst("'O':", "'<NOTABLE>':") + "}";
		
		//words dictionary
		for (Iterator<String> i = words2idx.keySet().iterator(); i.hasNext();){
			String word = (String)i.next();
			int wordIndex = words2idx.get(word);
			String sep = word.contains("'") ? "\"" : "'";
			
			if (wordDict.equals("'words2idx': {")){
				wordDict = wordDict + sep + word + sep + ": " + String.valueOf(wordIndex);
			}
			else{
				wordDict = wordDict + ", " + sep + word + sep + ": " + String.valueOf(wordIndex);
			}
		}
		wordDict = wordDict + "}";
		
		dicts = "{" + labelDict + ",\n " + tableDict + ",\n " + wordDict + "}";
		
		return dicts;
	}	
	
	//build script to generate a dataset with train (68% of the sentences), 
	//validation (17% of the sentences) and test (15% of the sentences) sets 
	private static void buildScript (String inputfile, String outputfile){
			
		loadIOBFile(inputfile);
		splitData(68, 17);
				
		//Generate the data
		String trainSet = buildTuple(trainSents, trainLabels);
		String validSet = buildTuple(validSents, validLabels);
		String testSet = buildTuple(testSents, testLabels);
		String dicts = buildDicts();
		
		//Build the script
		List<String> records = new ArrayList<String>();
		
		records.add("import cPickle as pickle\n\n");
		records.add("from numpy import *\n\n");
		records.add("train_set = " + trainSet + "\n\n");
		records.add("valid_set = " + validSet + "\n\n");
		records.add("test_set = " + testSet + "\n\n");
		records.add("dicts = " + dicts + "\n\n");
		records.add("data = train_set, valid_set, test_set, dicts\n\n");
		records.add("f = open('data_fold3.pkl', 'w')\n");
		records.add("pickle.dump(data, f)");
		
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
		System.out.println("Script saved in the output folder.");
	}
	
	public static void main (String args[]){
		
		buildScript("input/classified.txt", "output/data_gen_script.py");	
	}

}
