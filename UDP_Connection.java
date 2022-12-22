package application;

import static com.kuka.roboticsAPI.motionModel.BasicMotions.lin;
import static com.kuka.roboticsAPI.motionModel.BasicMotions.linRel;
import static com.kuka.roboticsAPI.motionModel.BasicMotions.ptp;
import static com.kuka.roboticsAPI.motionModel.BasicMotions.spl;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.company.UDP2SendBack;
import com.company.UDP_2;
import com.kuka.generated.ioAccess.ProbnaGrupa1IOGroup;
import com.kuka.roboticsAPI.applicationModel.RoboticsAPIApplication;
import com.kuka.roboticsAPI.controllerModel.Controller;
import com.kuka.roboticsAPI.deviceModel.LBR;
import com.kuka.roboticsAPI.geometricModel.Tool;
import com.kuka.roboticsAPI.motionModel.Spline;

public class UDP_Connection extends RoboticsAPIApplication{

	//robot stuff
	private Controller kuka_Sunrise_Cabinet_1;
	private LBR robot;
	private Tool gripper;
	public double velocityLimit;

	//UDP connection
	private DatagramSocket socket;
	private String messageMain = "";
	
	//TO send back
    public InetAddress address;
	public int port;
	
	HashMap<String, String> placesWithTools = new HashMap <String, String>();
	String whichTable;
	Spline splineToTable;
	Spline splineToWall;
	
	@Inject
	private ProbnaGrupa1IOGroup signalOut;

	@Override
	public void initialize() {
		kuka_Sunrise_Cabinet_1 = getController("KUKA_Sunrise_Cabinet_1");
		robot = (LBR) getDevice(kuka_Sunrise_Cabinet_1,
				"LBR_iiwa_7_R800_1");
		gripper = getApplicationData().createFromTemplate("Szczypaczki");
		gripper.attachTo(robot.getFlange());	
		placesWithTools.put("table1station1", "");
		placesWithTools.put("table1station2", "");
		placesWithTools.put("table1station3", "");
		placesWithTools.put("table1station4", "");
		placesWithTools.put("table2station1", "");
		placesWithTools.put("table2station2", "");
		placesWithTools.put("table2station3", "");
		placesWithTools.put("table2station4", "");
		whichTable = "1";
		splineToTable = new Spline(
				spl(getApplicationData().getFrame("/MiedzyStolami")),
				spl(getApplicationData().getFrame("/StolOdkladczy2/PunktPoczatkowy"))
					);
		splineToWall = new Spline(
				spl(getApplicationData().getFrame("/MiedzyStolami")),
				spl(getApplicationData().getFrame("/PunktZeroNadStolem"))
					);
		velocityLimit = 0.4;
		
	}

	public String receiveMessage() throws SocketException{
		socket = new DatagramSocket(30002);  //global DatagramSocket which we will use throughout to send packets, a byte array to wrap our messages, and a status variable called running
		UDP_2 udpServer = new UDP_2(socket);
		Thread thread = new Thread(udpServer);
		thread.start();
		getLogger().info("Waiting for message");
		try {
			thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		address = udpServer.address;
		port = udpServer.port;
		return udpServer.message;
	}

	public void SendMessageBack(String messageToSend){
		UDP2SendBack udpClient = new UDP2SendBack(socket, address, port, messageToSend);
		Thread thread = new Thread(udpClient);
		thread.start();
		try{
			thread.join();
			socket.close();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	public void OpenGripper(){
		signalOut.setZamykanieChwytaka(false);
		signalOut.setOtwieranieChwytaka(true);
	}
	public void CloseGripper(){
		signalOut.setOtwieranieChwytaka(false);
		signalOut.setZamykanieChwytaka(true);
	}
	public Runnable WaitThread(){
		try {
			TimeUnit.SECONDS.sleep((long) 1);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}
	public void WaitTimeAfterClosing(){
		Thread thread = new Thread(WaitThread());
		thread.start();
		try {
			thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	public void LightsControl(String controlString){
		if(controlString.equals("redOn")){
			signalOut.setLampkaRed(true);
		}else if(controlString.equals("redOff")){
			signalOut.setLampkaRed(false);
		}else if(controlString.equals("greenOn")){
			signalOut.setLampkaGreen(true);
		}else if(controlString.equals("greenOff")){
			signalOut.setLampkaGreen(false);
		}else if(controlString.equals("blueOn")){
			signalOut.setLampkaBlue(true);
		}else if(controlString.equals("blueOff")){
			signalOut.setLampkaBlue(false);
		}else{
			getLogger().info("There is not command like that: " + controlString);
		}
	}

	public void MoveByOffset(String moveDir){
		String offset = moveDir.substring(6);
		if(moveDir.substring(0, 5).equals("moveX")){
			gripper.moveAsync(linRel().setXOffset(Integer.valueOf(offset)));
		}else if(moveDir.substring(0, 5).equals("moveY")){
			gripper.moveAsync(linRel().setYOffset(Integer.valueOf(offset)));
		}else if(moveDir.substring(0, 5).equals("moveZ")){
			gripper.moveAsync(linRel().setZOffset(Integer.valueOf(offset)));
		}
	}
	
	public void GripperService(String order_){
		if(order_.equals("openGripper")){
			signalOut.setZamykanieChwytaka(false);
			signalOut.setOtwieranieChwytaka(true);
		}else if(order_.equals("closeGripper")){
			signalOut.setOtwieranieChwytaka(false);
			signalOut.setZamykanieChwytaka(true);
		}
	}
	
	public String isToolInUse(String tool){
		String[] tables = {"1","2"};
		String[] places = {"1","2","3"};
		for (String table : tables){
			for (String place : places){
				if(placesWithTools.get(String.format("table%sstation%s",table,place)).equals(tool)){
					return String.format("table%sstation%s",table,place);
				}
			}
		}
		return "notInUse";

	}
	
	public boolean isSomePlaceFree(){
		if(placesWithTools.get(String.format("table%sstation1",whichTable)).equals("") || placesWithTools.get(String.format("table%sstation2",whichTable)).equals("")
				|| placesWithTools.get(String.format("table%sstation3",whichTable)).equals("")){
			return true;
		}else{
			return false;
		}
	}
	
	public void takeScrewDriverFromWallToZeroPosistion(String whatTool){
		gripper.move(ptp(getApplicationData().getFrame("/PunktZeroNadStolem")).setJointVelocityRel(velocityLimit));
		gripper.move(ptp(getApplicationData().getFrame("/SciankaNarzedziowa/PunktPoczatkowySrubokrety")).setJointVelocityRel(velocityLimit));
		gripper.move(ptp(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowySrubokrety/Srubokret%sprzyg", whatTool))).setJointVelocityRel(velocityLimit));
		OpenGripper();
		gripper.move(lin(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowySrubokrety/Srubokret%sdol",whatTool))).setJointVelocityRel(velocityLimit));
		CloseGripper();
		WaitTimeAfterClosing();
		gripper.move(lin(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowySrubokrety/Srubokret%sgora",whatTool))).setJointVelocityRel(velocityLimit));
		gripper.move(lin(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowySrubokrety/Srubokret%sprzyg",whatTool))).setJointVelocityRel(velocityLimit));
		gripper.move(ptp(getApplicationData().getFrame("/PunktZeroNadStolem")).setJointVelocityRel(velocityLimit));
	}
	
	public void takeToolFromWallToZeroPosition(String whatTool){
		gripper.move(ptp(getApplicationData().getFrame("/SciankaNarzedziowa/PunktPoczatkowyKlucze")).setJointVelocityRel(velocityLimit));
		gripper.move(ptp(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowyKlucze/Klucz%sprzyg",whatTool))).setJointVelocityRel(velocityLimit));
		OpenGripper();
		gripper.move(lin(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowyKlucze/Klucz%s",whatTool))).setJointVelocityRel(velocityLimit));
		CloseGripper();
		WaitTimeAfterClosing();
		gripper.move(lin(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowyKlucze/Klucz%sprzyg",whatTool))).setJointVelocityRel(velocityLimit));
		gripper.move(ptp(getApplicationData().getFrame("/PunktZeroNadStolem")).setJointVelocityRel(velocityLimit));

	}
	public void takingToolFromZeroPositionToFreeSpace(String whatTool,String whatType){
		if(whichTable.equals("2")){
			gripper.move((splineToTable).setJointVelocityRel(velocityLimit));
		}
		gripper.move(ptp(getApplicationData().getFrame(String.format("/StolOdkladczy%s/place%srdy",whichTable, whatTool))).setJointVelocityRel(velocityLimit));
		gripper.move(lin(getApplicationData().getFrame(String.format("/StolOdkladczy%s/place%s%s",whichTable, whatTool,whatType))).setJointVelocityRel(velocityLimit));
		OpenGripper();
		WaitTimeAfterClosing();
		gripper.move(lin(getApplicationData().getFrame(String.format("/StolOdkladczy%s/place%srdy", whichTable, whatTool))).setJointVelocityRel(velocityLimit));
		if(whichTable.equals("2")){
			gripper.move(ptp(getApplicationData().getFrame("/StolOdkladczy2/PunktPoczatkowy")).setJointVelocityRel(velocityLimit));
			gripper.move((splineToWall).setJointVelocityRel(velocityLimit));
		}
		gripper.move(ptp(getApplicationData().getFrame("/PunktZeroNadStolem")).setJointVelocityRel(velocityLimit));
	}
	public void takeToolToFreePlace(String whatTool, String whatType)
	{
		// tool - nothing, smallScrewDriver - smallDriver, bigScrewDriver - bigDriver
		if(placesWithTools.get(String.format("table%sstation1",whichTable)).equals("")){
			placesWithTools.put(String.format("table%sstation1",whichTable), whatTool);
			//getLogger().info("robot is taking to station1: " + placesWithTools.get("station1"));
			takingToolFromZeroPositionToFreeSpace("1",whatType);
		}else if(placesWithTools.get(String.format("table%sstation2",whichTable)).equals("")){
			placesWithTools.put(String.format("table%sstation2",whichTable), whatTool);
			//getLogger().info("robot is taking to station2: " + placesWithTools.get("station2"));
			takingToolFromZeroPositionToFreeSpace("2",whatType);
		}else if(placesWithTools.get(String.format("table%sstation3",whichTable)).equals("")){
			placesWithTools.put(String.format("table%sstation3",whichTable), whatTool);
			//getLogger().info("robot is taking to station3: " + placesWithTools.get("station3"));
			takingToolFromZeroPositionToFreeSpace("3",whatType);
		}else{
			getLogger().info("no free space");
		}
	}
	
	
	public void takeToolFromTable(String whatTool, String whatType){
		gripper.move(ptp(getApplicationData().getFrame("/PunktZeroNadStolem")).setJointVelocityRel(velocityLimit));
		if(whichTable.equals("2")){
			gripper.move((splineToTable).setJointVelocityRel(velocityLimit));
		}
		OpenGripper();
		gripper.move(ptp(getApplicationData().getFrame(String.format("/StolOdkladczy%s/place4rdy",whichTable))).setJointVelocityRel(velocityLimit));
		gripper.move(lin(getApplicationData().getFrame(String.format("/StolOdkladczy%s/place4%s", whichTable,whatType))).setJointVelocityRel(velocityLimit));
		CloseGripper();
		WaitTimeAfterClosing();
		gripper.move(lin(getApplicationData().getFrame(String.format("/StolOdkladczy%s/place4rdy",whichTable))).setJointVelocityRel(velocityLimit/2));
		if(whichTable.equals("2")){
			gripper.move(ptp(getApplicationData().getFrame("/StolOdkladczy2/PunktPoczatkowy")).setJointVelocityRel(velocityLimit));
			gripper.move((splineToWall).setJointVelocityRel(velocityLimit));
		}
		gripper.move(ptp(getApplicationData().getFrame("/PunktZeroNadStolem")).setJointVelocityRel(velocityLimit/2));
		if(placesWithTools.get(String.format("table%sstation1",whichTable)).equals(whatTool)){
			placesWithTools.put(String.format("table%sstation1",whichTable), "");
		}else if(placesWithTools.get(String.format("table%sstation2",whichTable)).equals(whatTool)){
			placesWithTools.put(String.format("table%sstation2",whichTable), "");
		}else if(placesWithTools.get(String.format("table%sstation3",whichTable)).equals(whatTool)){
			placesWithTools.put(String.format("table%sstation3",whichTable), "");
		}else{
			getLogger().info("this tool is not in use");
		}
	}
	public void takeToolFromZeroPositionToitsplace(String whatTool){
		gripper.move(ptp(getApplicationData().getFrame("/SciankaNarzedziowa/PunktPoczatkowyKlucze"))
				.setJointVelocityRel(velocityLimit));
		gripper.move(lin(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowyKlucze/Klucz%sprzyg",
				whatTool))).setJointVelocityRel(velocityLimit));
		gripper.move(lin(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowyKlucze/Klucz%s",
				whatTool))).setJointVelocityRel(velocityLimit));
		OpenGripper();
		gripper.move(lin(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowyKlucze/Klucz%sprzyg",
				whatTool))).setJointVelocityRel(velocityLimit));
		gripper.move(ptp(getApplicationData().getFrame("/SciankaNarzedziowa/PunktPoczatkowyKlucze"))
				.setJointVelocityRel(velocityLimit));
		gripper.move(ptp(getApplicationData().getFrame("/PunktZeroNadStolem")).setJointVelocityRel(velocityLimit));
	}
	public void takeScrevDriverFromZeroPositionToitsplace(String whatTool){
		gripper.move(ptp(getApplicationData().getFrame("/SciankaNarzedziowa/PunktPoczatkowySrubokrety")).setJointVelocityRel(velocityLimit));
		gripper.move(ptp(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowySrubokrety/Srubokret%sprzyg", whatTool))).setJointVelocityRel(velocityLimit));
		gripper.move(lin(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowySrubokrety/Srubokret%sGoraOdkl",whatTool))).setJointVelocityRel(velocityLimit));
		gripper.move(lin(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowySrubokrety/Srubokret%sDolOdkl",whatTool))).setJointVelocityRel(velocityLimit));
		OpenGripper();
		gripper.move(lin(getApplicationData().getFrame(String.format("/SciankaNarzedziowa/PunktPoczatkowySrubokrety/Srubokret%sprzyg",whatTool))).setJointVelocityRel(velocityLimit));
		gripper.move(ptp(getApplicationData().getFrame("/PunktZeroNadStolem")).setJointVelocityRel(velocityLimit));
	}

	public void screwDriver(String tool, String toolNumber){
		LightsControl("greenOff");
		String whereIsTool = isToolInUse(tool);
		//Warunek jezeli narzedzie znajduje sie na wybranym stole
		if(whereIsTool.equals(String.format("table%sstation1",  whichTable))||
				whereIsTool.equals(String.format("table%sstation2",  whichTable))||
				whereIsTool.equals(String.format("table%sstation3",  whichTable))){
			LightsControl("redOff");
			if(Integer.valueOf(toolNumber)<8){
				takeToolFromTable(tool,"smallDriver");
			}else{
				takeToolFromTable(tool,"bigDriver");
			}
			takeScrevDriverFromZeroPositionToitsplace(toolNumber);
		}else if(whereIsTool.equals("notInUse")){
			if(isSomePlaceFree()){
				takeScrewDriverFromWallToZeroPosistion(toolNumber);
				if(Integer.valueOf(toolNumber)<8){
					takeToolToFreePlace(tool,"smallDriver");
				}else{
					takeToolToFreePlace(tool,"bigDriver");
				}
			}
		}else{
			getLogger().info(String.format("The tool is on another table: %s!  Actual table: %s. Change table first",whereIsTool,whichTable));
		}
	}
	
	public void toolWork(String tool, String toolNumber){
		LightsControl("greenOff");
		String whereIsTool = isToolInUse(tool);
		if(whereIsTool.equals(String.format("table%sstation1",  whichTable))||
				whereIsTool.equals(String.format("table%sstation2",  whichTable))||
				whereIsTool.equals(String.format("table%sstation3",  whichTable))){
			LightsControl("redOff");
			takeToolFromTable(tool,"");
			takeToolFromZeroPositionToitsplace(toolNumber);

		}else if((whereIsTool.equals("notInUse"))){
			if(isSomePlaceFree()){
				takeToolFromWallToZeroPosition(toolNumber);
				takeToolToFreePlace(tool,"");
			}
		}else{
			getLogger().info(String.format("The tool is on another table: %s! Actual table: %s. Change table first",whereIsTool,whichTable));
		}
	}
	@Override
	public void run() {
		//robot.setESMState("2");		//clean
		robot.setESMState("3");	//collision detection, velocity monitoring, force TCP monitoring
		//robot.setESMState("4"); // safe space monitoring
		gripper.move(ptp(getApplicationData().getFrame("/PunktZeroNadStolem")).setJointVelocityRel(velocityLimit));
		Boolean firstMessage = true;
		while(!messageMain.equals("endProgram")){
			try {
				if(!isSomePlaceFree()){
					LightsControl("redOn");
				}else{
					LightsControl("redOff");
					LightsControl("greenOn");
				}
				if(!firstMessage){
					SendMessageBack("ready2go");
				}
				messageMain = receiveMessage();
				firstMessage = false;
				getLogger().info("Message in main: " + messageMain);
				if(messageMain.length() >=5 && (messageMain.substring(0, 5).equals("moveX") || messageMain.substring(0, 5).equals("moveY") || messageMain.substring(0, 5).equals("moveZ"))){
					MoveByOffset(messageMain);
				}
				else if(messageMain.equals("closeGripper") || messageMain.equals("openGripper"))
				{
					GripperService(messageMain);
				}else{
					if(messageMain.equals("srubokret jeden"))
					{
						screwDriver("screwDriver1","1");
					}
					else if(messageMain.equals("srubokret dwa"))
					{
						screwDriver("screwDriver2","2");
					}
					else if(messageMain.equals("srubokret trzy"))
					{
						screwDriver("screwDriver3","3");
					}
					else if(messageMain.equals("srubokret cztery"))
					{
						screwDriver("screwDriver4","4");
					}
					else if(messageMain.equals("srubokret piec"))
					{
						screwDriver("screwDriver5","5");
					}
					else if(messageMain.equals("srubokret szesc"))
					{
						screwDriver("screwDriver6","6");
					}
					else if(messageMain.equals("srubokret siedem"))
					{
						screwDriver("screwDriver7","7");
					}
					else if(messageMain.equals("srubokret osiem"))
					{
						screwDriver("screwDriver8","8");
					}
					else if(messageMain.equals("srubokret dziewiec"))
					{
						screwDriver("screwDriver9","9");
					}
					else if(messageMain.equals("srubokret dziesiec"))
					{
						screwDriver("screwDriver10","10");
					}
					else if(messageMain.equals("srubokret jedenascie"))
					{
						screwDriver("screwDriver11","11");
					}
					else if(messageMain.equals("srubokret dwanascie"))
					{
						screwDriver("screwDriver12","12");
					}
					else if(messageMain.equals("klucz dwadziescia jeden"))
					{
						toolWork("Tool21","1");
					}
					else if(messageMain.equals("klucz dziewietnascie"))
					{
						toolWork("Tool19","2");
					}
					else if(messageMain.equals("klucz osiemnascie"))
					{
						toolWork("Tool18","3");
					}
					else if(messageMain.equals("klucz siedemnascie"))
					{
						toolWork("Tool17","4");
					}
					else if(messageMain.equals("stol jeden")){
						whichTable = "1";
					}
					else if(messageMain.equals("stol dwa")){
						whichTable = "2";
					}
					else{
						LightsControl(messageMain);
					}
				}
			} catch (SocketException e) {
				e.printStackTrace();
			}
		}
		SendMessageBack("bye bye");
		LightsControl("greenOff");
		LightsControl("redOff");
	}

	@Override
	public void dispose()
	{
		getLogger().info("wylaczenie funkcji");
		socket.close();
	}


	public static void main(String[] args) {
		RobotApplication app = new RobotApplication();
		app.runApplication();
	}
}

