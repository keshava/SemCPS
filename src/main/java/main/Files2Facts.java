
package main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.util.FileManager;

import uni.bonn.krextor.Krextor;
import util.ConfigManager;
import util.StringUtil;

/**
 * Reads the RDF files and convert them to Datalog facts or to PSL facts
 * 
 * @author Irlan 28.06.2016
 */
public class Files2Facts extends IndustryStandards {
	private RDFNode object;
	private RDFNode predicate;
	private RDFNode subject;

	private ArrayList<File> files;
	private Set<String> forInternalElements;
	private PrintWriter fromDocumentwriter;
	private PrintWriter attributeWriter;
	private PrintWriter hasRefSemanticwriter;
	private PrintWriter hasIDwriter;
	private PrintWriter internalElementwriter;
	private Model model;
	private LinkedHashSet<String> forAttribute;
	private LinkedHashSet<String> forID;
	private LinkedHashSet<String> forRefSemantic;
	private LinkedHashSet<String> forDocument;

	/**
	 * Converts the file to turtle format based on Krextor
	 * 
	 * @param input
	 * @param output
	 */
	public void convert2RDF() {
		int i = 0;
		for (File file : files) {
			if (file.getName().endsWith(".aml")) {
				Krextor krextor = new Krextor();
				krextor.convertRdf(file.getAbsolutePath(), "aml", "turtle",
						ConfigManager.getFilePath() + "plfile" + i + ".ttl");
			} else {
				Krextor krextor = new Krextor();
				krextor.convertRdf(file.getAbsolutePath(), "opcua", "turtle",
						ConfigManager.getFilePath() + "plfile" + i + ".ttl");
			}
			i++;
		}
	}

	/**
	 * Read the RDF files of a given path
	 * 
	 * @param path
	 * @return
	 * @throws Exception
	 */
	public ArrayList<File> readFiles(String path, String type, String type2, String type3) throws Exception {
		files = new ArrayList<File>();
		File originalFilesFolder = new File(path);
		if (originalFilesFolder.isDirectory()) {
			for (File amlFile : originalFilesFolder.listFiles()) {
				if (amlFile.isFile() && (amlFile.getName().endsWith(type) || amlFile.getName().endsWith(type2)
						|| amlFile.getName().endsWith(type3))) {
					if (amlFile.getName().endsWith(".aml")) {
						String name = amlFile.getName().replace(".aml", "");
						if (name.endsWith("0") || name.endsWith("1")) {
							files.add(amlFile);
						}
					}

					else if (amlFile.getName().endsWith(".opcua")) {
						String name = amlFile.getName().replace(".opcua", "");
						if (name.endsWith("0") || name.endsWith("1")) {
							files.add(amlFile);
						}
					}

					else if (amlFile.getName().endsWith(".xml")) {
						String name = amlFile.getName().replace(".xml", "");
						files.add(amlFile);
					}

					else {
						files.add(amlFile);
					}
				}
			}
		} else {
			System.out.println("Error in the directory that you provided");
			System.exit(0);
		}
		return files;
	}

	/**
	 * Adds a better turtle format for the obtained RDF files
	 * 
	 * @throws IOException
	 */
	public void improveRDFOutputFormat() throws IOException {
		for (File file : files) {
			if (file.getName().endsWith(".ttl")) {
				org.apache.jena.rdf.model.Model model = RDFDataMgr.loadModel(file.getAbsolutePath());
				File replaceFile = new File(file.getParent() + "/" + file.getName());
				FileOutputStream out = new FileOutputStream(replaceFile, false);
				RDFDataMgr.write(out, model, RDFFormat.TURTLE_BLOCKS);
				out.close();
			}
		}
	}

	/**
	 * Reads the turtle format RDF files and extract the contents for data log
	 * conversion.
	 * 
	 * @param file
	 * @param number
	 * @return
	 * @throws Exception
	 */
	public String factsFromFiles(File file, int number) throws Exception {

		StringBuilder buf = new StringBuilder();
		InputStream inputStream = FileManager.get().open(file.getAbsolutePath());

		Model model = null;
		model = ModelFactory.createDefaultModel();

		model.read(new InputStreamReader(inputStream), null, "TURTLE");
		StmtIterator iterator = model.listStatements();

		while (iterator.hasNext()) {
			Statement stmt = iterator.nextStatement();
			subject = stmt.getSubject();
			predicate = stmt.getPredicate();
			object = stmt.getObject();

			buf.append("clause1(").append(StringUtil.lowerCaseFirstChar(predicate.asNode().getLocalName())).append("(")
					.append(StringUtil.lowerCaseFirstChar(subject.asNode().getLocalName()) + number).append(",");
			if (object.isURIResource()) {
				object = model.getResource(object.as(Resource.class).getURI());
				String objectStr = object.asNode().getLocalName();
				if (predicate.asNode().getLocalName().toString().equals("type")) {
					buf.append(StringUtil.lowerCaseFirstChar(objectStr));
				} else {
					buf.append(StringUtil.lowerCaseFirstChar(objectStr) + number);
				}

			} else {
				if (object.isLiteral()) {
					buf.append("'" + object.asLiteral().getLexicalForm() + "'");

				} else {
					buf.append(object);

				}
			}

			buf.append("),true).");
			buf.append(System.getProperty("line.separator"));

		}

		return buf.toString();
	}

	/**
	 * Reads the RDF files and extract the contents for creating PSL predicates.
	 * 
	 * @param file
	 * @param number
	 * @return
	 * @throws Exception
	 */
	public String createPSLPredicate(File file, int number, PrintWriter fromDocumentwriter, PrintWriter attributewriter,
			PrintWriter hasRefSemanticwriter, PrintWriter hasIDwriter, PrintWriter internalElementwriter)
			throws Exception {

		InputStream inputStream = FileManager.get().open(file.getAbsolutePath());
		model = ModelFactory.createDefaultModel();
		model.read(new InputStreamReader(inputStream), null, "TURTLE");
		StmtIterator iterator = model.listStatements();
		StmtIterator subjectIterator = model.listStatements();

		// init data structures
		initDataStructs();

		ArrayList<Resource> allSubjects = getAllSubjects(subjectIterator);

		while (iterator.hasNext()) {

			Statement stmt = iterator.nextStatement();
			subject = stmt.getSubject();
			predicate = stmt.getPredicate();
			object = stmt.getObject();

			// all subjects are added accordign to ontology e.g aml: opcua:
			// forDocument.txt
			addSubjectURI(subject, forDocument, "");

			addsDataforAML(); // process required data for AML
			Opcua opcua = new Opcua(subject, object, predicate, model, forAttribute, forID, forRefSemantic,
					forInternalElements);

			opcua.addsDataforOPCUA(allSubjects); // process required data for
													// opcua
		}

		/**
		 * Write data to files
		 */
		writeData(forDocument, fromDocumentwriter);
		writeData(forAttribute, attributewriter);
		writeData(forInternalElements, internalElementwriter);
		writeData(forRefSemantic, hasRefSemanticwriter);
		writeData(forID, hasIDwriter);

		return "";
	}

	/**
	 * Writes data to files
	 * 
	 * @param docName
	 * @param documentwriter
	 */
	private void writeData(Set<String> docName, PrintWriter documentwriter) {
		for (String i : docName) {
			// remove annotation to make it a literal value
			if (i.contains("aml:remove")) {
				i = i.replace("aml:remove", "");
			}
			if (i.contains("opcua:remove")) {
				i = i.replace("opcua:remove", "");
			}
			documentwriter.println(i);
		}
	}

	/**
	 * OPCUA data is complex and requires matching with parent node
	 * 
	 * @param allSubjects
	 */

	/**
	 * Automation ML part for data population
	 */

	private void addsDataforAML() {
		// TODO Auto-generated method stub

		// RefSemantic part starts here
		if (predicate.asNode().getLocalName().equals("hasRefSemantic")) {
			// adds for attribute.txt
			addSubjectURI(subject, forAttribute, ":" + object.asNode().getLocalName());
			// adds for refsemantic.txt
			addRefSemantic();

		}

		// RefSemantic part starts here
		if (predicate.asNode().getLocalName().equals("identifier")) {

			// id is for internal Element goes in InternalElement file
			if (subject.asNode().getLocalName().contains("InternalElement")) {
				addSubjectURI(subject, forInternalElements, ":" + predicate.asNode().getLocalName());
			} else {
				// id is for attribute goes in forAttribute
				addSubjectURI(subject, forAttribute, ":" + predicate.asNode().getLocalName());
			}
			// gets the literal ID value and add it to forID
			addSubjectURI(subject, forID, ":remove" + object.asLiteral().getLexicalForm());
		}
	}

	/**
	 * Add refsemantic in the list
	 */
	private void addRefSemantic() {
		StmtIterator stmts = model.listStatements(object.asResource(), null, (RDFNode) null);
		while (stmts.hasNext()) {

			Statement stmte = stmts.nextStatement();
			// remove because its a literal so we can identify. we dont need
			// aml:
			// opcua: tags
			if (stmte.getObject().isLiteral()) {
				addSubjectURI(object, forRefSemantic, ":remove" + stmte.getObject().asLiteral().getLexicalForm());
			}
		}
	}

	/**
	 * Get all subjects for opcua
	 * 
	 * @param subjectIterator
	 * @return
	 */
	private ArrayList<Resource> getAllSubjects(StmtIterator subjectIterator) {
		ArrayList<Resource> allSubjects = new ArrayList<Resource>();
		while (subjectIterator.hasNext()) {
			Statement stmt = subjectIterator.nextStatement();
			subject = stmt.getSubject();
			if (subject.asNode().getNameSpace().contains("aml")) {
				break;
			}
			allSubjects.add(subject.asResource());
		}
		return allSubjects;
	}

	/**
	 * Initialises data structures
	 */
	private void initDataStructs() {
		forDocument = new LinkedHashSet<>();
		forAttribute = new LinkedHashSet<>();
		forID = new LinkedHashSet<>();
		forRefSemantic = new LinkedHashSet<>();
		forInternalElements = new LinkedHashSet<>();
	}

	/**
	 * Generate all the files of a given folder
	 * 
	 * @throws Exception
	 */
	public void generateExtensionalDB(String path) throws Exception {
		int i = 1;
		StringBuilder buf = new StringBuilder();
		for (File file : files) {
			buf.append(factsFromFiles(file, i++));
		}
		PrintWriter prologWriter = new PrintWriter(new File(path + "edb.pl"));
		prologWriter.println(buf);
		prologWriter.close();
	}

	/**
	 * Generate PSL predicates
	 * 
	 * @param path
	 * @throws Exception
	 */
	public void generatePSLPredicates(String path) throws Exception {
		int i = 1;
		initFileWriters();
		for (File file : files) {
			// pass in the writers
			createPSLPredicate(file, i++, fromDocumentwriter, attributeWriter, hasRefSemanticwriter, hasIDwriter,
					internalElementwriter);
		}

		closeFileWriters();

	}

	/**
	 * Closes writers
	 */

	private void closeFileWriters() {
		// TODO Auto-generated method stub
		fromDocumentwriter.close();
		attributeWriter.close();
		hasRefSemanticwriter.close();
		hasIDwriter.close();
		internalElementwriter.close();

	}

	/**
	 * Initialises writers
	 * 
	 * @throws FileNotFoundException
	 */

	private void initFileWriters() throws FileNotFoundException {
		fromDocumentwriter = new PrintWriter("data/ontology/test/fromDocument.txt");
		attributeWriter = new PrintWriter("data/ontology/test/Attribute.txt");
		hasRefSemanticwriter = new PrintWriter("data/ontology/test/hasRefsemantic.txt");
		hasIDwriter = new PrintWriter("data/ontology/test/hasID.txt");
		internalElementwriter = new PrintWriter("data/ontology/test/InternalElements.txt");
	}

	/**
	 * Creates temporary files which holds the path for edb.pl and output.txt
	 * These files are necessary for evalAML.pl so that the path is
	 * automatically set from config.ttl
	 * 
	 * @throws FileNotFoundException
	 */
	public void prologFilePath() throws FileNotFoundException {
		PrintWriter prologWriter = new PrintWriter(
				new File(System.getProperty("user.dir") + "/resources/files/edb.txt"));
		prologWriter.println("'" + ConfigManager.getFilePath() + "edb.pl" + "'.");
		prologWriter.close();

		prologWriter = new PrintWriter(new File(System.getProperty("user.dir") + "/resources/files/output.txt"));
		prologWriter.println("'" + ConfigManager.getFilePath() + "output.txt" + "'.");
		prologWriter.close();

	}

}