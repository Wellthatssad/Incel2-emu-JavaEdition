package me.wellthatssad.incel2emu.emu;


import me.wellthatssad.incel2emu.emu.component.PortComponent;
import me.wellthatssad.incel2emu.emu.component.components.RandomNumGen;
import me.wellthatssad.incel2emu.emu.component.components.Stdin;
import me.wellthatssad.incel2emu.emu.component.components.Stdout;
import me.wellthatssad.incel2emu.utils.InstBuffer;
import me.wellthatssad.incel2emu.utils.OpcodeConverter;
import me.wellthatssad.incel2emu.utils.Operations;

import java.util.Arrays;
import java.util.Stack;

public class Incel2 implements Runnable {
    //I would use shorts but Java is a bitch(it does not have unsigned integer types)
    public final static int OPCODE_MASK = 0b1111100000000000;
    public final static int DEST_MASK = 0b0000011100000000;
    public final static int SRC1_MASK = 0b0000000000111000;
    public final static int SRC2_MASK = 0b0000000000000111;
    public final static int FLAG_MASK = 0b0000011000000000;
    public final static int IMM_MASK = 0b0000000011111111;
    public final static int V_MASK = 0b0000000100000000;
    public final static int P_MASK = (short) 0b0000010000000000;

    //defining stuff
    private final int REG_COUNT = 8;
    private final int FLAG_COUNT = 4;
    private final int PORT_COUNT = 7;
    private final int RAM_SIZE = 64;
    private final int ROM_SIZE = 256;
    private final int STACK_SIZE = 10;
    private final int BIT_COUNT = 8;
    private final int RAM_INDEX_REG = 7;

    //mem
    private final int[] registers = new int[REG_COUNT];
    private final boolean[] flags = new boolean[FLAG_COUNT];
    private final PortComponent[] portsI = new PortComponent[PORT_COUNT];
    private final PortComponent[] portsO = new PortComponent[PORT_COUNT];
    private final int[] ram = new int[RAM_SIZE];
    private final int[] prom = new int[ROM_SIZE];
    private int IP = 0;
    private final Stack<Integer> callStack = new Stack<>();
    private boolean running = false;
    private int exitCode = 0;

    //decoding regs
    private int currInst;
    private Operations opcode;
    private int dst;
    private int src1;
    private int src2;
    private int flag;
    private int imm;
    private boolean v;
    private boolean p;

    public Incel2(){
        portsI[0] = new RandomNumGen((int) System.currentTimeMillis());
        portsI[1] = new Stdin();
        portsO[0] = new Stdout();
        IP = 0;
    }

    public void addComponent(PortComponent component, int port){
        if(port > PORT_COUNT){
            System.out.println("Port number exceeds the maximum port index");
            return;
        }
        if(component.isInput()){
            portsI[port] = component;
        }
        else {
            portsO[port] = component;
        }
    }

    private void fetch(){
        if(IP >= ROM_SIZE){
            exitCode = -1;
            System.out.println("Error during fetching: reached maximum rom size before halt instruction");
            halt();
        }
        currInst = prom[IP++];
    }

    private void decode(){
        if(!running){
            return;
        }
        opcode = OpcodeConverter.convert((currInst & OPCODE_MASK) >> 11);
        dst = ((currInst & DEST_MASK) >> 8);
        src1 = ((currInst & SRC1_MASK) >> 3);
        src2 = ((currInst & SRC2_MASK));
        flag = ((currInst & FLAG_MASK) >> 9);
        imm = ((currInst & IMM_MASK));
        v =  (((currInst & V_MASK) >> 8) >= 1);
        p = (((currInst & P_MASK) >> 10) >= 1);
        if(opcode == null){
            exitCode = -1;
            halt();
        }
    }

    private void execute(){
        boolean updateFlags = false;
        if(!running){
            return;
        }
        switch(opcode){
            case NOOP:
                break;
            case ADD:
                System.out.println("ADD: " + registers[dst] + " " + registers[src1] + " " +  registers[src2]);
                registers[dst] = (registers[src1] + registers[src2]) % (int) Math.pow(2, BIT_COUNT);
                flags[2] = (registers[src1] + registers[src2]) > 255;
                updateFlags = true;
                System.out.println("result: " + registers[dst]);
                break;
            case SUB:
                System.out.println("SUB: " + dst + " " + src1 + " " + src2);
                registers[dst] = (registers[src1] - registers[src2]) % (int) Math.pow(2, BIT_COUNT);
                flags[2] = (registers[src1] - registers[src2]) >= 0;
                updateFlags = true;
                break;
            case AND:
                System.out.println("AND: " + dst + " " + src1 + " " + src2);
                registers[dst] = (registers[src1] & registers[src2] );
                updateFlags = true;
                break;
            case OR:
                System.out.println("OR: " + dst + " " + src1 + " " + src2);
                registers[dst] = (registers[src1] | registers[src2]);
                updateFlags = true;
                break;
            case ADC:
                System.out.println("ADC: " + dst + " " + src1 + " " + src2);
                if(flags[2]){
                    registers[dst] = (registers[src1] + registers[src2] + 1) % (int) Math.pow(2, BIT_COUNT);
                    flags[2] = (registers[src1] + registers[src2] + 1) > 255;
                }
                else {
                    registers[dst] = (registers[src1] + registers[src2]) % (int) Math.pow(2, BIT_COUNT);
                    flags[2] = (registers[src1] + registers[src2]) > 255;

                }
                updateFlags = true;
                break;
            case RSH:
                System.out.println("RSH: " + dst + " " + src1);
                registers[dst] = registers[src1] >>1;
                flags[2] = registers[src1] % 2 != 0;
                break;
            case ADI:
                System.out.println("ADI: " + dst + " " + " " + imm);
                int temp = registers[dst];
                registers[dst] = (registers[dst] + imm) % (int) Math.pow(2, BIT_COUNT);
                flags[2] = temp + imm > 255;
                updateFlags = true;
                break;
            case ANDI:
                System.out.println("ANDI: " + dst + " " + " " + imm);
                registers[dst] &= imm;
                updateFlags = true;
                break;
            case XORI:
                System.out.println("XORI: " + dst + " " + " " + imm);
                registers[dst] ^= imm;
                updateFlags = true;
                break;
            case LDI:
                System.out.println("LDI: " + dst + " " + " " + imm);
                registers[dst] = imm;
                break;
            case MST:
                int setIndex = (registers[RAM_INDEX_REG] & 0b00111111);
                System.out.println("MST: " + setIndex + " " + " " + src1);
                ram[setIndex] = registers[src1];
                break;
            case MLD:
                int loadIndex = (registers[RAM_INDEX_REG] & 0b00111111);
                System.out.println("MLD: " + dst + " " + loadIndex);
                registers[dst] = ram[loadIndex];
                break;
            case PST:

                break;
            case PLD:

                break;
            case BRC:
                System.out.println("BRC: " + flag + " " + v + " " + imm);
                if(flags[flag] == v){
                    IP = imm;
                }
                break;
            case JMP:
                System.out.println("JMP: " + p + " " + imm);
                if(p) {
                    System.out.printf("SP: %d , Val: %d\n", callStack.size(), callStack.peek());
                    IP = callStack.pop();
                }
                else
                    IP = imm;
                break;
            case JAL:
                System.out.println("JAL: " + callStack.size() + " " + imm);
                callStack.push(IP);
                IP = imm;
                break;
            case HLT:
                System.out.println("HLT: ");
                exitCode = 0;
                halt();
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + opcode);
        }
        if(updateFlags){
            flags[0] = (registers[dst] > 127);
            flags[1] = (registers[dst] == 0);
            flags[3] = ((registers[dst] % 2) == 1);
        }
        registers[0] = 0;
    }

    public void start(){
        running = true;
        new Thread(this).start();
    }
    public void halt(){
        System.out.println("Incel2 halted with exit code: " + exitCode);
        running = false;
    }
    @Override
    public void run() {
        int cycle = 1;
        while(running){
            System.out.println("Cycle: " + cycle);
            fetch();
            decode();
            execute();
            System.out.println(Arrays.toString(flags));
            System.out.println(Arrays.toString(registers));
            displayRam();
            cycle++;
            System.out.println();
        }
    }

    public void displayRam(){
        for(int i = 48; i < RAM_SIZE; i++){
            System.out.println("Address " + i + ": " +  ram[i]);
        }
    }
    public void loadProgram(InstBuffer buff){
        if(buff.buff.size() >= ROM_SIZE){
            System.out.println("Invalid program: program size is bigger than ROM size :(");
            return;
        }
        IP = 0;
        for(int i = 0; i < buff.buff.size(); i++){
            prom[i] = buff.buff.get(i);
        }
    }
}
