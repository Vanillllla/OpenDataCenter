package odc;

/** Resource requests are local to one machine. Network is expressed in MB/s in this catalog. */
final class Workload {
    final int tier,cpu,gpu,vram,ram,disk,io,score;
    final double net,x;
    final double[] weights;
    Workload(int tier,int cpu,int gpu,int vram,int ram,int disk,int io,double net,double x,int score,double[] weights){
        this.tier=tier;this.cpu=cpu;this.gpu=gpu;this.vram=vram;this.ram=ram;this.disk=disk;this.io=io;this.net=net;this.x=x;this.score=score;this.weights=weights;
    }
    static final double[] W0={3,0,0,1,0}; // CPU-майнинг
    static final double[] W1={1,1,1,1,0}; // GPU-майнинг
    static final double[] W2={1,3,1,1,0.5}; // Рендеринг
    static final double[] W3={1,2,3,2,0.5}; // Искусственный интеллект
    static final double[] W4={5,0,0,2,0.5}; // Игровой хостинг
    static final double[] W5={3,0,0,2,1}; // Виртуализация
    static final double[] W6={0.5,0,0,0.5,1}; // Хранилища
    static final double[] W7={2,0,0,3,3}; // Базы данных
    static final double[] W8={4,0,0,2,0.5}; // Научные расчёты
    static final double[] W9={1,0,0,1,2}; // CDN
    static final double[] W10={3,0,0,1,0}; // Сетевые услуги
    static final double[] W11={2,0,0,1,1}; // Общие вычисления
    static final Workload[] ALL={
        new Workload(1,1,0,0,1,0,0,0.1,1.0,0,W0), // CPU mining
        new Workload(1,1,1,2,1,0,0,1,1.5,0,W1), // GPU mining
        new Workload(1,1,0,0,2,128,20,2,1.2,0,W6), // Blockchain storage
        new Workload(2,4,0,0,4,0,0,1,5.0,0,W0), // Advanced CPU mining
        new Workload(2,1,1,8,2,0,0,2,2.5,0,W1), // Advanced GPU mining
        new Workload(2,1,0,0,2,1024,50,5,3.5,0,W6), // Disk farming
        new Workload(3,8,0,0,16,32,100,5,12.0,0,W8), // Cryptographic proofs
        new Workload(3,2,1,16,4,0,0,5,4.5,0,W1), // High-performance GPU mining
        new Workload(3,4,0,0,32,512,500,50,8.0,0,W7), // Blockchain validation
        new Workload(1,2,0,0,2,64,50,5,1.0,300,W11), // Data archiving
        new Workload(1,2,1,2,4,16,50,5,1.5,500,W2), // Image rendering
        new Workload(1,2,0,0,4,32,100,5,1.2,400,W11), // Software compilation
        new Workload(2,2,1,8,8,64,100,10,2.5,2500,W2), // 3D animation rendering
        new Workload(2,4,1,6,8,128,200,20,3.0,3000,W2), // Video processing
        new Workload(2,8,0,0,16,64,100,10,4.0,4000,W8), // Scientific computing
        new Workload(2,4,1,16,16,128,300,20,5.0,6000,W3), // Neural network training
        new Workload(3,32,0,0,64,256,500,50,8.0,15000,W8), // Climate simulation
        new Workload(3,4,1,24,32,256,500,50,7.0,12000,W2), // Film rendering
        new Workload(3,8,1,48,64,512,1000,100,10.0,25000,W3), // Large language model training
        new Workload(3,16,0,0,128,128,500,20,12.0,30000,W8), // Quantum simulation
        new Workload(3,16,2,32,128,1024,1500,200,15.0,50000,W3), // Multimodal AI training
        new Workload(1,1,0,0,1,8,5,5,1.0,0,W10), // Web hosting
        new Workload(1,1,0,0,1,4,5,25,1.5,0,W10), // VPN hosting
        new Workload(1,1,0,0,1,128,20,10,1.8,0,W6), // Cloud storage
        new Workload(1,1,0,0,2,32,10,5,1.2,0,W11), // Mail server
        new Workload(2,2,0,0,8,32,50,10,3.5,0,W4), // Game hosting
        new Workload(2,2,0,0,4,64,50,20,3.0,0,W5), // VPS rental
        new Workload(2,4,0,0,16,128,300,20,6.0,0,W7), // Database hosting
        new Workload(2,1,0,0,2,1024,100,50,5.0,0,W6), // Backups
        new Workload(2,4,1,8,8,128,100,25,7.0,0,W2), // Cloud gaming
        new Workload(2,2,0,0,4,256,200,250,6.0,0,W9), // CDN server
        new Workload(3,8,0,0,32,256,300,100,14.0,0,W5), // Enterprise virtualization
        new Workload(3,4,1,24,16,128,500,50,12.0,0,W3), // AI assistants
        new Workload(3,8,1,48,64,256,1000,100,22.0,0,W3), // LLM hosting
        new Workload(3,4,1,16,16,128,300,50,10.0,0,W3), // Image generation
        new Workload(3,4,1,8,16,512,500,500,18.0,0,W2), // Video streaming
        new Workload(3,4,0,0,16,4096,500,250,16.0,0,W6), // Enterprise cloud storage
        new Workload(3,8,0,0,32,1024,2000,2000,25.0,0,W9), // Enterprise CDN
    };
    static boolean contract(int i){return i>=9 && i<21;}
    static boolean service(int i){return i>=21;}
}
