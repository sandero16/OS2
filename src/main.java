import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.sun.org.apache.xpath.internal.SourceTree;
import javafx.scene.control.Tab;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class Process {
	int pid;
	List<TablePageEntry> pageTable;
	int gealloceerd;
	Set<Integer> framenummers = new HashSet<Integer>();

	public Process(int p) {
		pid = p;
		pageTable = new ArrayList<TablePageEntry>();
		TablePageEntry t;
		for (int a = 0; a < 16; a++) {
			pageTable.add(new TablePageEntry());
		}
	}
	public void printFramenummers(){
		for(Integer i: framenummers) {
			System.out.print(i+" ");
		}
	}
	public void printTable(){
		System.out.println("size"+pageTable.size());
		for(TablePageEntry tpe : pageTable){
			tpe.printEntry();
			System.out.println();
		}
	}

	public List<Integer> verwijderFrames(int aantal) {
		List<Integer> vrijgekomenPlaatsen = new ArrayList<Integer>();
		Comparator<TablePageEntry> ATcomp = new AccesTimeComparator();

		// Alle pageEntries die in het RAM zitten in de priorityqueue steken.
		// Vervolgens de eerste 'aantal' pages van deze queue eruit halen
		// Stel lijst te klein, kiezen voor random idle frames weg te geven.
		PriorityQueue<TablePageEntry> ATqueue = new PriorityQueue<TablePageEntry>(16, ATcomp);

		for (TablePageEntry tpe : pageTable) {
			if (tpe.getPresentBit() == 1) {
				ATqueue.add(tpe);
			}
		}
		int verwijderd = 0;
		int aantalOver = 0;
		for (int a = 0; a < aantal; a++) {
			if (!ATqueue.isEmpty()) {
				ATqueue.peek().setPresentBit(0);
				ATqueue.peek().setModifyBit(0);
				framenummers.remove(ATqueue.peek().getFrameNummer());
				vrijgekomenPlaatsen.add(ATqueue.peek().getFrameNummer());
				ATqueue.remove();
				verwijderd++;
			}
		}
		aantalOver = aantal - verwijderd;
		List<Integer> teVerwijderenFrames=new ArrayList<Integer>();
		for (Integer i : framenummers) {
			if (aantalOver > 0) {
				vrijgekomenPlaatsen.add(i);
				teVerwijderenFrames.add(i);
				aantalOver--;
			} else {
				break;
			}
		}
		for(Integer i: teVerwijderenFrames){
			framenummers.remove(i);
		}
		return vrijgekomenPlaatsen;

	}
	public boolean checkAanwezigFrame(int page, boolean write, int clock){
		if(pageTable.get(page).getPresentBit()==1){
			if(write){
				pageTable.get(page).setModifyBit(1);
				pageTable.get(page).setLastAccesTime(clock);
			}
			return true;
		}
		return false;
	}
	public void vervangLU(int page, boolean write,int clock){
		//we zoeken de of er nog vrije gealoceerde ruimte is in het RAM
		int count=0;
		for(TablePageEntry t: pageTable){
			if(t.getPresentBit()==1)count++;
		}
		if(count==framenummers.size()){
			//indien alle gealloceerde frames gebruikt worden zoeken we LRU frame en vervangen we deze
			boolean first=true;
			TablePageEntry LU=null;
			TablePageEntry temp=null;
			int index=-1;
		for(int j=0;j<pageTable.size();j++) {
			temp=pageTable.get(j);
			if (temp.getPresentBit() == 1) {
				if (first) {
					LU = temp;
					index=j;
					first = false;
				} else if (temp.getLastAccesTime() < LU.getLastAccesTime()){
					LU = temp;
					index=j;
				}
			}

			}
			pageTable.get(index).setPresentBit(0);
			pageTable.get(index).setModifyBit(0);
			int frame=pageTable.get(index).getFrameNummer();
			useFrame(page,clock,write,frame);

		}
		else{
			boolean free=true;
			for(Integer i: framenummers){
				for(int j=0;j<pageTable.size();j++){
					if(pageTable.get(j).getFrameNummer()==i&&pageTable.get(j).getPresentBit()==1){
						free=false;
					}
				}
				if(free){
					useFrame(page,clock,write,i);
					break;
				}
			}
		}
	}

	public void setGealloceerd(int a) {
		gealloceerd = a;
	}

	public int getMaxAccesTime() {
		int max = 0;
		for (TablePageEntry tpe : pageTable) {
			if (tpe.getLastAccesTime() > max && tpe.getPresentBit()==1) {
				max=tpe.getLastAccesTime();
			}
		}
		return max;
	}

	public List<Integer> removeOutOfRam() {
		List<Integer> lijst = new ArrayList<Integer>();
		for(TablePageEntry tpe:pageTable) {
			if(tpe.getFrameNummer()!=-1) {lijst.add(tpe.getFrameNummer());}
			tpe.setFrameNummer(-1);
			tpe.setPresentBit(0);
			tpe.setModifyBit(0);
			//SchrijfOpdrachtenVerhoogd
		}
		return lijst;

	}

	public void getInRam(List<Integer> lijst) {
		framenummers = new HashSet<Integer>();
		for(Integer i: lijst) {
			framenummers.add(i);
		}


	}
	public void useFrame(int page,int clock,boolean write, int frame){
		pageTable.get(page).setLastAccesTime(clock);
		pageTable.get(page).setPresentBit(1);
		pageTable.get(page).setFrameNummer(frame);
		if(write)pageTable.get(page).setModifyBit(1);
		else pageTable.get(page).setModifyBit(0);
	}
	public void addFrame(Integer integer) {
		framenummers.add(integer);

	}


}
class AccesTimeComparator implements Comparator<TablePageEntry> {
	@Override
	public int compare(TablePageEntry o1, TablePageEntry o2) {
		return o1.getLastAccesTime() - o2.getLastAccesTime();
	}
}

class TablePageEntry {
	int presentBit;
	int modifyBit;
	int lastAccesTime;
	int frameNummer;

	public TablePageEntry() {
		frameNummer = -1;
		lastAccesTime = -1;
		presentBit = 0;
		modifyBit = 0;
	}

	public TablePageEntry(int p, int m, int l, int f) {
		presentBit = p;
		modifyBit = m;
		lastAccesTime = l;
		frameNummer = f;
	}
	public void setTableEntry(int clock, boolean write, int frame){
		presentBit=1;
		lastAccesTime=clock;
		frameNummer=frame;
		if(write)modifyBit=1;
		else modifyBit=0;
	}
	public void printEntry(){
		System.out.print("presentbit: "+presentBit+" modifybit: "+modifyBit+" framenummer:"+frameNummer+" la: "+lastAccesTime);
	}
	public int getPresentBit() {
		return presentBit;
	}

	public void setPresentBit(int presentBit) {
		this.presentBit = presentBit;
	}

	public int getModifyBit() {
		return modifyBit;
	}

	public void setModifyBit(int modifyBit) {
		this.modifyBit = modifyBit;
	}

	public int getLastAccesTime() {
		return lastAccesTime;
	}

	public void setLastAccesTime(int lastAccesTime) {
		this.lastAccesTime = lastAccesTime;
	}

	public int getFrameNummer() {
		return frameNummer;
	}

	public void setFrameNummer(int frameNummer) {
		this.frameNummer = frameNummer;
	}
}

class Instructie {
	String operation;
	int pid;
	int adress;

	public Instructie() {
	}

	public Instructie(int a, String o, int b) {
		a = pid;
		operation = o;
		adress = b;
	}
	public int getPid(){
		return  pid;
	}
	public int getAdress(){
		return adress;
	}

}

class Ram{
	int aantalProc;
	int[] processen;
	Set<Integer> processenIds = new HashSet<Integer>();
	
	public Ram() {
		processen = new int[12];
		aantalProc=0;
	}
	public void printRam(){
		for(int i=0;i<12;i++){
			System.out.print(processen[i]+" ");
		}
		System.out.println();
	}
	public int getFrameFrom(int pid){
		for(int i=0;i<processen.length;i++){
			if(processen[i]==pid)return i;
		}
		return 0;
	}
	public void nieuwProcess(int id, List<Process> processenlijst) {
		if (aantalProc == 4) {
			int laagsteClock = 100000000;
			int pid = 0;
			for (Integer i : processenIds) {
				if (processenlijst.get(i).getMaxAccesTime() < laagsteClock) {
					pid = i;
					laagsteClock = processenlijst.get(i).getMaxAccesTime();
				}
			}
			processenIds.remove(pid);
			processenIds.add(id);
			List<Integer> lijst = processenlijst.get(pid).removeOutOfRam();
			processenlijst.get(id).getInRam(lijst);

			for (Integer i : lijst) {
				processen[i] = id;
			}

		} else if (aantalProc == 0) {
			List<Integer> alleFrames = new ArrayList<Integer>();
			alleFrames.addAll(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));
			processenlijst.get(id).getInRam(alleFrames);
			for (int a = 0; a < 12; a++) {
				processen[a] = id;
			}
			processenIds.add(id);
			aantalProc++;
		} else {

			List<Integer> vrijgekomenFrames = new ArrayList<Integer>();
			int teVerwijderenPerProcess = (12 / aantalProc - 12 / (aantalProc + 1));
			System.out.println("size"+processenIds.size());
			for (Integer i : processenIds) {
				vrijgekomenFrames.addAll(processenlijst.get(i).verwijderFrames(teVerwijderenPerProcess));
				for(Integer ids: vrijgekomenFrames){
					System.out.println("vrij: "+ids);
				}
				System.out.println();
			}
			for (Integer i : vrijgekomenFrames) {
				processen[i] = id;
			}

			processenlijst.get(id).getInRam(vrijgekomenFrames);
			processenIds.add(id);
			aantalProc++;
		}
	}


	public int getAantalProc() {
		return aantalProc;
	}

	public void setAantalProc(int aantalProc) {
		this.aantalProc = aantalProc;
	}

	public int[] getProcessen() {
		return processen;
	}

	public void setProcessen(int[] processen) {
		this.processen = processen;
	}

	public void verwijderProcess(int pid, List<Process> processenlijst) {
		List<Integer> vrijgekomenFrames = new ArrayList<Integer>();
		for (int a = 0; a < 12; a++) {
			if (processen[a] == pid) {
				processen[a] = -1;
				vrijgekomenFrames.add(a);
			}
		}
		aantalProc--;
		processenIds.remove(pid);

		for (Integer i : processenIds) {
			for (int a = 0; a < 12 / ((aantalProc + 1) * aantalProc); a++) {
				processenlijst.get(i).addFrame(vrijgekomenFrames.get(0));
				processen[vrijgekomenFrames.get(0)]=i;
				vrijgekomenFrames.remove(vrijgekomenFrames.get(0));
			}
		}
		processenlijst.get(pid).removeOutOfRam();
		//SchrijvenNaarPersistentGeheugen++

	}
}

public class main {
	static int clock;
	static Ram RAM = new Ram();
	static List<Process> processenlijst = new ArrayList<Process>();
	static int st;
	static int pid;
	
	public static void main(String[] args) {
		clock=0;
		String at;
		Instructie p;
		List<Instructie> instructielijst = new ArrayList<Instructie>();

		Map<String, Runnable> functies = new HashMap<String, Runnable>();
		functies.put("Start", () -> doeStart());
		functies.put("Read", () -> doeRead());
		functies.put("Write", () -> doeWrite());
		functies.put("Terminate", () -> doeTerminate());

		

		try {

			File fXmlFile = new File("Instructions_30_3.xml");
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fXmlFile);

			doc.getDocumentElement().normalize();

			System.out.println("Root element :" + doc.getDocumentElement().getNodeName());

			NodeList nList = doc.getElementsByTagName("instruction");

			System.out.println("----------------------------");

			for (int temp = 0; temp < nList.getLength(); temp++) {

				Node nNode = nList.item(temp);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;

					pid = Integer.parseInt(eElement.getElementsByTagName("processID").item(0).getTextContent());
					at = eElement.getElementsByTagName("operation").item(0).getTextContent();
					st = Integer.parseInt(eElement.getElementsByTagName("address").item(0).getTextContent());

					p = new Instructie(pid, at, st);
					instructielijst.add(p);
					functies.get(at).run();
					RAM.printRam();
					for(int k=0;k<processenlijst.size();k++){
						processenlijst.get(k).printTable();
					}
					clock++;

				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		/*for(int i=0;i<instructielijst.size();i++){
			if(instructielijst.get(i).ge)
		}*/

	}

	public static void doeStart() {

		System.out.println("Ik doe start");
		Process p = new Process(pid);
		processenlijst.add(pid, p);
		LRUStart(-1,false);

	}

	public static void doeRead() {
		System.out.println("Ik doe read");
		int page=getPage(st);
		//we kijken of de page nog niet aanwezig is in het RAM geheugen
		if(!processenlijst.get(pid).checkAanwezigFrame(page,false,clock)){
			//indien er nog geen page van dit proces in het RAM geheugen zit moet er plaats worden gemaakt
			if(processenlijst.get(pid).framenummers.size()==0){
				LRUStart(page,false);
			}
			else{
				LRUReadWrite(page,false);
			}
		};

	}

	public static void doeWrite() {
		System.out.println("Ik doe write");
		int frame=getPage(st);
		if(!processenlijst.get(pid).checkAanwezigFrame(frame,true,clock)){
			if(processenlijst.get(pid).framenummers.size()==0){
				System.out.println("hier");
				LRUStart(frame,true);
			}
			else{
				System.out.println("rw");
				LRUReadWrite(frame,true);
			}
		}

	}

	public static void doeTerminate() {
		System.out.println("Ik doe terminate");
		RAM.verwijderProcess(pid,processenlijst);

	}


	public static void LRUStart(int page,boolean write) {

		System.out.println("LRU");
		for(Process process: processenlijst){
			process.printFramenummers();
		}
		RAM.nieuwProcess(pid, processenlijst);
		int frame=RAM.getFrameFrom(pid);
		if(page!=-1) {
			processenlijst.get(pid).useFrame(page,clock,write,frame);

		}
		/*
		 * 4 processen in ram -> 1 proces verwijderen => met laagste totale acces Time
		 * 0-3 processen => per proces de 2^(3-n) met laagste acces Time
		 */

	}
	public static void LRULaagstTotaal(){
		/*
		 * totale acces time van fragments in proces optellen -> gene met laagste vervangen
		 * 
		 * 
		 */
	}
	public static void LRULaagsteFragments(int aantalProc){
		/*
		 * de 2^(3-n) fragments met laagste accesTime uit ram halen
		 */
	}
	public static void LRUReadWrite(int page, boolean write){
		/*
		 * fragment met laagste accesTime dat van proces zelf is
		 */

		for(int i=0;i<processenlijst.size();i++){
			System.out.println("*************************************"+" "+i);
			processenlijst.get(i).printFramenummers();
		}
		processenlijst.get(pid).vervangLU(page,write,clock);
		System.out.println("LRU");



	}
	public static int getPage(int st){
		double temp=(double)st/4096;
		//indien makkelijker-> veranderen
		int temp2=st/4096;
		double offset=(temp-(double)temp2)*4096;

		return temp2;
	}

	public static int aantalProcInRam(){
		return 1;
	}
}
