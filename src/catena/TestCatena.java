package catena;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import catena.evaluator.PairEvaluator;
import catena.parser.entities.CLINK;
import catena.parser.entities.TLINK;
import catena.parser.entities.TemporalRelation;
import jdk.internal.org.xml.sax.SAXException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class TestCatena {

public static void main(String[] args) throws Exception {
		
		String task = "tbdense";
		boolean colFilesAvailable = false;
		boolean train = false;
		
		switch(task) {
		
			case "te3" :
				TempEval3(colFilesAvailable, train);
				break;
				
			case "tbdense" :
				TimeBankDense(colFilesAvailable, train);
				break;
		}
		
	}
	
	public static void TempEval3(boolean colFilesAvailable, boolean train) throws Exception {
		
		String taskName = "te3";
		Catena cat = new Catena(true, true);
		
		// ---------- TEMPORAL ---------- //
		
		String[] te3CLabel = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		String[] te3CLabelCollapsed = {"BEFORE", "AFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		
		Temporal temp = new Temporal(false, te3CLabelCollapsed,
				"./models/" + taskName + "-event-dct.model",
				"./models/" + taskName + "-event-timex.model",
				"./models/" + taskName + "-event-event.model",
				true, true, true,
				true, false);
		
		// TRAIN
		if (train) {
			System.err.println("Train temporal model...");
			
			Map<String, String> relTypeMappingTrain = new HashMap<String, String>();
			relTypeMappingTrain.put("DURING", "SIMULTANEOUS");
			relTypeMappingTrain.put("DURING_INV", "SIMULTANEOUS");
			relTypeMappingTrain.put("IBEFORE", "BEFORE");
			relTypeMappingTrain.put("IAFTER", "AFTER");
			temp.trainModels(taskName, "./data/TempEval3-train_TML/", te3CLabelCollapsed, relTypeMappingTrain, colFilesAvailable);
		}
		
		// PREDICT
		Map<String, String> relTypeMapping = new HashMap<String, String>();
		relTypeMapping.put("IDENTITY", "SIMULTANEOUS");
		List<TLINK> tlinks = temp.extractRelations(taskName, "./data/TempEval3-eval_TML/", te3CLabelCollapsed, relTypeMapping, colFilesAvailable);
		
		// ---------- CAUSAL ---------- //
		
		Map<String, Map<String, String>> clinkPerFile = Causal.getCausalTempEval3EvalTlinks("./data/Causal-TempEval3-eval.txt");
		
		String[] causalLabel = {"CLINK", "CLINK-R", "NONE"};
		
		Causal causal = new Causal(
				"./models/" + taskName + "-causal-event-event.model",
				true, true);
		
		// TRAIN
		if (train) {
			System.err.println("Train causal model...");
			
			Map<String, String> relTypeMappingTrain = new HashMap<String, String>();
			relTypeMappingTrain.put("DURING", "SIMULTANEOUS");
			relTypeMappingTrain.put("DURING_INV", "SIMULTANEOUS");
			relTypeMappingTrain.put("IBEFORE", "BEFORE");
			relTypeMappingTrain.put("IAFTER", "AFTER");
			
			Map<String, Map<String, String>> tlinksForClinkTrainPerFile = new HashMap<String, Map<String, String>>();
			if (cat.isTlinkFeature()) {	
				List<TLINK> tlinksTrain = temp.extractRelations(taskName, "./data/Causal-TimeBank_TML/", te3CLabelCollapsed, relTypeMappingTrain, colFilesAvailable);
				for (String s : tlinksTrain.get(0).getEE()) {
					String[] cols = s.split("\t");
					if (!tlinksForClinkTrainPerFile.containsKey(cols[0])) tlinksForClinkTrainPerFile.put(cols[0], new HashMap<String, String>());
					tlinksForClinkTrainPerFile.get(cols[0]).put(cols[1]+","+cols[2], cols[3]);
					tlinksForClinkTrainPerFile.get(cols[0]).put(cols[2]+","+cols[1], TemporalRelation.getInverseRelation(cols[3]));
				}
				causal.trainModels(taskName, "./data/Causal-TimeBank_TML/", causalLabel, 
						cat.isTlinkFeature(), tlinksForClinkTrainPerFile, te3CLabelCollapsed, colFilesAvailable);
			} else {
				causal.trainModels(taskName, "./data/Causal-TimeBank_TML/", causalLabel, colFilesAvailable);
			}
		}
		
		// PREDICT
		CLINK clinks;
		Map<String, Map<String, String>> tlinksForClinkPerFile = new HashMap<String, Map<String, String>>(); 
		if (cat.isTlinkFeature()) {
			Map<String, String> relTypeMappingTrain = new HashMap<String, String>();
			relTypeMappingTrain.put("DURING", "SIMULTANEOUS");
			relTypeMappingTrain.put("DURING_INV", "SIMULTANEOUS");
			relTypeMappingTrain.put("IBEFORE", "BEFORE");
			relTypeMappingTrain.put("IAFTER", "AFTER");
			
			for (String s : tlinks.get(0).getEE()) {
				String[] cols = s.split("\t");
				if (!tlinksForClinkPerFile.containsKey(cols[0])) tlinksForClinkPerFile.put(cols[0], new HashMap<String, String>());
				String label = cols[4];
				for (String key : relTypeMappingTrain.keySet()) {
					label = label.replace(key, relTypeMappingTrain.get(key));
				}
				tlinksForClinkPerFile.get(cols[0]).put(cols[1]+","+cols[2], label);
				tlinksForClinkPerFile.get(cols[0]).put(cols[2]+","+cols[1], TemporalRelation.getInverseRelation(label));
			}
			clinks = causal.extractRelations(taskName, "./data/TempEval3-eval_TML/", clinkPerFile, causalLabel, 
					cat.isTlinkFeature(), tlinksForClinkPerFile, te3CLabelCollapsed, colFilesAvailable);
		} else {
			clinks = causal.extractRelations(taskName, "./data/TempEval3-eval_TML/", clinkPerFile, causalLabel, colFilesAvailable);
		}
		
		
		// POST-EDITING
		if (cat.isClinkPostEditing()) {
			for (String key : clinks.getEELinks().keySet()) {
				if (clinks.getEELinks().get(key).equals("CLINK")) {
					if (tlinks.get(1).getEELinks().containsKey(key)) {
						tlinks.get(1).getEELinks().put(key, "BEFORE");
					}
				} else if (clinks.getEELinks().get(key).equals("CLINK-R")) {
					if (tlinks.get(1).getEELinks().containsKey(key)) {
						tlinks.get(1).getEELinks().put(key, "AFTER");
					}
				}
			}
		}
		
		
		// EVALUATE
		System.out.println("********** EVALUATION RESULTS **********");
		System.out.println();
		System.out.println("********** TLINK TIMEX-TIMEX ***********");
		PairEvaluator ptt = new PairEvaluator(tlinks.get(1).getTT());
		ptt.evaluatePerLabel(te3CLabel);
		System.out.println();
		System.out.println("*********** TLINK EVENT-DCT ************");
		PairEvaluator ped = new PairEvaluator(tlinks.get(1).getED());
		ped.evaluatePerLabel(te3CLabel);
		System.out.println();
		System.out.println("********** TLINK EVENT-TIMEX ***********");
		PairEvaluator pet = new PairEvaluator(tlinks.get(1).getET());
		pet.evaluatePerLabel(te3CLabel);
		System.out.println();
		System.out.println("********** TLINK EVENT-EVENT ***********");
		PairEvaluator pee = new PairEvaluator(tlinks.get(1).getEE());
		pee.evaluatePerLabel(te3CLabel);
		System.out.println();
		System.out.println("********** CLINK EVENT-EVENT ***********");
		PairEvaluator peec = new PairEvaluator(clinks.getEE());
		peec.evaluatePerLabel(causalLabel);
	}
	
	public static void TimeBankDense(boolean colFilesAvailable, boolean train) throws Exception {
		
		String[] devDocs = { 
			"APW19980227.0487.tml", 
			"CNN19980223.1130.0960.tml", 
			"NYT19980212.0019.tml",  
			"PRI19980216.2000.0170.tml", 
			"ed980111.1130.0089.tml" 
		};
			
//		String[] testDocs = {
//			"APW19980227.0489.tml",
//			"APW19980227.0494.tml",
//			"APW19980308.0201.tml",
//			"APW19980418.0210.tml",
//			"CNN19980126.1600.1104.tml",
//			"CNN19980213.2130.0155.tml",
//			"NYT19980402.0453.tml",
//			"PRI19980115.2000.0186.tml",
//			"PRI19980306.2000.1675.tml"
//		};
		String[] testDocs = {
				"2010.01.01.iran.moussavi.tml",
				"2010.01.02.pakistan.attacks.tml",
				"2010.01.03.japan.jal.airlines.ft.tml",
				"2010.01.06.tennis.qatar.federer.nadal.tml",
				"2010.01.07.water.justice.tml",
				"2010.01.07.winter.weather.tml",
				"2010.01.12.uk.islamist.group.ban.tml",
				"2010.01.13.haiti.un.mission.tml",
				"2010.01.18.sherlock.holmes.tourism.london.tml",
				"2010.01.18.uk.israel.livni.tml"
		};
		
		String[] trainDocs = {
			"APW19980219.0476.tml",
			"ea980120.1830.0071.tml",
			"PRI19980205.2000.1998.tml",
			"ABC19980108.1830.0711.tml",
			"AP900815-0044.tml",
			"CNN19980227.2130.0067.tml",
			"NYT19980206.0460.tml",
			"APW19980213.1310.tml",
			"AP900816-0139.tml",
			"APW19980227.0476.tml",
			"PRI19980205.2000.1890.tml",
			"CNN19980222.1130.0084.tml",
			"APW19980227.0468.tml",
			"PRI19980213.2000.0313.tml",
			"ABC19980120.1830.0957.tml",
			"ABC19980304.1830.1636.tml",
			"APW19980213.1320.tml",
			"PRI19980121.2000.2591.tml",
			"ABC19980114.1830.0611.tml",
			"APW19980213.1380.tml",
			"ea980120.1830.0456.tml",
			"NYT19980206.0466.tml"
		};
		
//		Map<String, Map<String, String>> tlinkPerFile = Temporal.getTimeBankDenseTlinks("./data/TimebankDense.T3.txt");
		Map<String, Map<String, String>> tlinkPerFile = new HashMap<>();

		String taskName = "tbdense";
		Catena cat = new Catena(true, true);
		
		// ---------- TEMPORAL ---------- //
		
		String[] tbDenseLabel = {"BEFORE", "AFTER", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "VAGUE"};
		
		String[] te3CLabel = {"BEFORE", "AFTER", "IBEFORE", "IAFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "DURING", "DURING_INV", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		String[] te3CLabelCollapsed = {"BEFORE", "AFTER", "IDENTITY", "SIMULTANEOUS", 
				"INCLUDES", "IS_INCLUDED", "BEGINS", "BEGUN_BY", "ENDS", "ENDED_BY"};
		
		Temporal temp = new Temporal(true, tbDenseLabel,
				"./models/" + taskName + "-event-dct.model",
				"./models/" + taskName + "-event-timex.model",
				"./models/" + taskName + "-event-event.model",
				true, true, true,
				true, false);
		
		// TRAIN
		if (train) {
			System.err.println("Train temporal model...");
			
			temp.trainModels(taskName, "./data/TempEval3-train_TML/", trainDocs, tlinkPerFile, tbDenseLabel, colFilesAvailable);
		}
		
		// PREDICT
		Map<String, String> relTypeMapping = new HashMap<String, String>();
		relTypeMapping.put("IDENTITY", "SIMULTANEOUS");
		relTypeMapping.put("BEGINS", "BEFORE");
		relTypeMapping.put("BEGUN_BY", "AFTER");
		relTypeMapping.put("ENDS", "AFTER");
		relTypeMapping.put("ENDED_BY", "BEFORE");
		relTypeMapping.put("DURING", "SIMULTANEOUS");
		relTypeMapping.put("DURING_INV", "SIMULTANEOUS");
		//List<TLINK> tlinks = temp.extractRelations(taskName, "./data/TempEval3-train_TML/", testDocs, tlinkPerFile, tbDenseLabel, relTypeMapping, colFilesAvailable);
		List<TLINK> tlinks = temp.extractRelations(taskName, "./data/quang_gold_temporal/", testDocs, tlinkPerFile, tbDenseLabel, relTypeMapping, colFilesAvailable);

		// ---------- CAUSAL ---------- //
		
		Map<String, Map<String, String>> clinkPerFile = Causal.getCausalTempEval3EvalTlinks("./data/Causal-TempEval3-eval.txt");
		
		String[] causalLabel = {"CLINK", "CLINK-R", "NONE"};
		
		Causal causal = new Causal(
				"./models/" + taskName + "-causal-event-event.model",
				true, true);
		
		// TRAIN
		if (train) {
			System.err.println("Train causal model...");
			
			Map<String, String> relTypeMappingTrain = new HashMap<String, String>();
			relTypeMappingTrain.put("DURING", "SIMULTANEOUS");
			relTypeMappingTrain.put("DURING_INV", "SIMULTANEOUS");
			relTypeMappingTrain.put("IBEFORE", "BEFORE");
			relTypeMappingTrain.put("IAFTER", "AFTER");
			
			Map<String, Map<String, String>> tlinksForClinkTrainPerFile = new HashMap<String, Map<String, String>>();
			if (cat.isTlinkFeature()) {	
				List<TLINK> tlinksTrain = temp.extractRelations(taskName, "./data/Causal-TimeBank_TML/", te3CLabelCollapsed, relTypeMappingTrain, colFilesAvailable);
				for (String s : tlinksTrain.get(0).getEE()) {
					String[] cols = s.split("\t");
					if (!tlinksForClinkTrainPerFile.containsKey(cols[0])) tlinksForClinkTrainPerFile.put(cols[0], new HashMap<String, String>());
					tlinksForClinkTrainPerFile.get(cols[0]).put(cols[1]+","+cols[2], cols[3]);
					tlinksForClinkTrainPerFile.get(cols[0]).put(cols[2]+","+cols[1], TemporalRelation.getInverseRelation(cols[3]));
				}
				causal.trainModels(taskName, "./data/Causal-TimeBank_TML/", testDocs, causalLabel,
						cat.isTlinkFeature(), tlinksForClinkTrainPerFile, te3CLabelCollapsed, colFilesAvailable);
			} else {
				causal.trainModels(taskName, "./data/Causal-TimeBank_TML/", testDocs, causalLabel, colFilesAvailable);
			}
		}
		
		// PREDICT
		CLINK clinks;
		Map<String, Map<String, String>> tlinksForClinkPerFile = new HashMap<String, Map<String, String>>(); 
		if (cat.isTlinkFeature()) {
			Map<String, String> relTypeMappingTrain = new HashMap<String, String>();
			relTypeMappingTrain.put("DURING", "SIMULTANEOUS");
			relTypeMappingTrain.put("DURING_INV", "SIMULTANEOUS");
			relTypeMappingTrain.put("IBEFORE", "BEFORE");
			relTypeMappingTrain.put("IAFTER", "AFTER");
			
			for (String s : tlinks.get(0).getEE()) {
				String[] cols = s.split("\t");
				if (!tlinksForClinkPerFile.containsKey(cols[0])) tlinksForClinkPerFile.put(cols[0], new HashMap<String, String>());
				String label = cols[4];
				for (String key : relTypeMappingTrain.keySet()) {
					label = label.replace(key, relTypeMappingTrain.get(key));
				}
				tlinksForClinkPerFile.get(cols[0]).put(cols[1]+","+cols[2], label);
				tlinksForClinkPerFile.get(cols[0]).put(cols[2]+","+cols[1], TemporalRelation.getInverseRelation(label));
			}
			
//			clinks = causal.extractRelations(taskName, "./data/Causal-TimeBank_TML/", testDocs, causalLabel,
//					cat.isTlinkFeature(), tlinksForClinkPerFile, te3CLabelCollapsed, colFilesAvailable);
			clinks = causal.extractRelations(taskName, "./data/quang_gold_temporal/", testDocs, causalLabel,
					cat.isTlinkFeature(), tlinksForClinkPerFile, te3CLabelCollapsed, colFilesAvailable);
		} else {
			clinks = causal.extractRelations(taskName, "./data/Causal-TimeBank_TML/", testDocs, causalLabel, colFilesAvailable);
		}
		
		
		// POST-EDITING
		if (cat.isClinkPostEditing()) {
			for (String key : clinks.getEELinks().keySet()) {
				if (clinks.getEELinks().get(key).equals("CLINK")) {
					if (tlinks.get(1).getEELinks().containsKey(key)) {
						tlinks.get(1).getEELinks().put(key, "BEFORE");
					}
				} else if (clinks.getEELinks().get(key).equals("CLINK-R")) {
					if (tlinks.get(1).getEELinks().containsKey(key)) {
						tlinks.get(1).getEELinks().put(key, "AFTER");
					}
				}
			}
		}
		
		
		// EVALUATE
		System.out.println("********** EVALUATION RESULTS **********");
		System.out.println();
		System.out.println("********** TLINK TIMEX-TIMEX ***********");
		PairEvaluator ptt = new PairEvaluator(tlinks.get(1).getTT());
		ptt.evaluatePerLabel(tbDenseLabel);
		System.out.println();
		System.out.println("*********** TLINK EVENT-DCT ************");
		PairEvaluator ped = new PairEvaluator(tlinks.get(1).getED());
		ped.evaluatePerLabel(tbDenseLabel);
		System.out.println();
		System.out.println("********** TLINK EVENT-TIMEX ***********");
		PairEvaluator pet = new PairEvaluator(tlinks.get(1).getET());
		pet.evaluatePerLabel(tbDenseLabel);
		System.out.println();
		System.out.println("********** TLINK EVENT-EVENT ***********");
		PairEvaluator pee = new PairEvaluator(tlinks.get(1).getEE());
		pee.evaluatePerLabel(tbDenseLabel);
		System.out.println();
		System.out.println("********** CLINK EVENT-EVENT ***********");
		PairEvaluator peec = new PairEvaluator(clinks.getEE());
		peec.evaluatePerLabel(causalLabel);
		TestCatena.writeTlinks("./data/quang_gold_temporal/", Arrays.asList(testDocs), tlinks.get(1), "./data/quang_gold_temporal_catena_temporal_causal");
	}

	public static void writeTlinks(String foldername, TLINK tlinks, String outputFolder) throws ParserConfigurationException, IOException, SAXException, org.xml.sax.SAXException {
		writeTlinks(foldername, new ArrayList<String>(), tlinks, outputFolder);
	}

	public static void writeTlinks(String foldername, List<String> includeFiles, TLINK tlinks, String outputFolder) throws ParserConfigurationException, IOException, SAXException, FileNotFoundException, ParserConfigurationException, org.xml.sax.SAXException {
		int lid = 0;
		File folder = new File(foldername);
		File[] listOfFiles = folder.listFiles();
		HashMap<String, HashMap<String, String>> res = new HashMap<>();
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		HashMap<String, HashMap<String, String>> eventInstanceMap = new HashMap<>();
		HashMap<String, String> fileContentMap = new HashMap<>();
		File outDir = new File(outputFolder);
		if (!outDir.exists()) {
			outDir.mkdir();
		}

		for (File file : listOfFiles) {
			if (file.getName().endsWith(".tml") && includeFiles.contains(file.getName())) {
				byte[] encoded = Files.readAllBytes(Paths.get(file.getCanonicalPath()));
				String fileContent = new String(encoded, StandardCharsets.UTF_8);
				Document document = builder.parse(new InputSource(new StringReader(fileContent)));
				Element rootElement = document.getDocumentElement();
				NodeList nodeList = document.getElementsByTagName("*");
				for (int i = 0; i < nodeList.getLength(); i++) {
					Node currentNode = nodeList.item(i);
					if (currentNode.getNodeName().indexOf("MAKEINSTANCE") != -1) {
						if (!eventInstanceMap.containsKey(file.getName())) {
							eventInstanceMap.put(file.getName(), new HashMap<>());
						}
						Element currElement = (Element) currentNode;
						String eiid = currElement.getAttribute("eiid");
						String eid = currElement.getAttribute("eventID");
						eventInstanceMap.get(file.getName()).put(eid, eiid);
					}
				}

				int tlinkStartIdx = fileContent.indexOf("<TLINK");
				String usefulContent = fileContent.substring(0, tlinkStartIdx);
				fileContentMap.put(file.getName(), usefulContent);
			}
		}

		for (String line : tlinks.getED()) {
			String[] splitted = line.split("\t");
			String fname = splitted[0];
			String e1 = splitted[1];
			String predictedLabel = splitted[4];
			String eii1 = eventInstanceMap.get(fname).get(e1);
			if (eii1 == null) {
				System.out.println(e1);
			}
			String eii2 = "t0";
			String newContent = fileContentMap.get(fname) + "\n" +
					String.format("<TLINK eventInstanceID=\"%s\" lid=\"%s\" relType=\"%s\" relatedToTime=\"%s\"/>\n",
							eii1, "l" + lid++, predictedLabel, eii2);

			fileContentMap.put(fname, newContent);
		}

		for (String line : tlinks.getET()) {
			String[] splitted = line.split("\t");
			String fname = splitted[0];
			String e1 = splitted[1];
			String e2 = splitted[2];
			String predictedLabel = splitted[4];
			String eii1 = eventInstanceMap.get(fname).get(e1);
			String eii2 = "t" + e2.substring(3);
			String newContent = fileContentMap.get(fname) + "\n" +
					String.format("<TLINK eventInstanceID=\"%s\" lid=\"%s\" relType=\"%s\" relatedToTime=\"%s\"/>\n",
							eii1, "l" + lid++, predictedLabel, eii2);
			fileContentMap.put(fname, newContent);
		}

		for (String line : tlinks.getTT()) {
			String[] splitted = line.split("\t");
			String fname = splitted[0];
			String e1 = splitted[1];
			String e2 = splitted[2];
			String predictedLabel = splitted[4];
			String eii1 = "t" + e1.substring(3);
			String eii2 = "t" + e2.substring(3);
			String newContent = fileContentMap.get(fname) + "\n" +
					String.format("<TLINK timeID=\"%s\" lid=\"%s\" relType=\"%s\" relatedToTime=\"%s\"/>\n",
							eii1, "l" + lid++, predictedLabel, eii2);
			fileContentMap.put(fname, newContent);
		}

		for (String line : tlinks.getEE()) {
			String[] splitted = line.split("\t");
			String fname = splitted[0];
			String e1 = splitted[1];
			String e2 = splitted[2];
			String predictedLabel = splitted[4];
			String eii1 = eventInstanceMap.get(fname).get(e1);
			String eii2 = eventInstanceMap.get(fname).get(e2);
			String newContent = fileContentMap.get(fname) + "\n" +
					String.format("<TLINK eventInstanceID=\"%s\" lid=\"%s\" relType=\"%s\" relatedToEventInstance=\"%s\"/>\n",
							eii1, "l" + lid++, predictedLabel, eii2);
			fileContentMap.put(fname, newContent);
		}

		for (Map.Entry<String, String> entry : fileContentMap.entrySet()) {
			String content = entry.getValue() + "</TimeML>";
			try {
				PrintStream ps = new PrintStream(outputFolder + "/" + entry.getKey());
				ps.print(content);
				ps.close();
			} catch (FileNotFoundException e) {
				System.err.println("Unable to open file");
			}
		}
	}
	
}
