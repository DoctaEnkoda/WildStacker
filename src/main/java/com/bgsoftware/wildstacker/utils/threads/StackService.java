package com.bgsoftware.wildstacker.utils.threads;

import com.bgsoftware.wildstacker.api.objects.StackedItem;
import com.bgsoftware.wildstacker.api.objects.StackedObject;
import com.google.common.collect.Maps;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings({"WeakerAccess", "BooleanMethodIsAlwaysInverted"})
public final class StackService {

    private static final Map<String, StackServiceWorld> stackServiceWorldMap = Maps.newConcurrentMap();

    public static void execute(StackedObject stackedObject, Runnable runnable){
        execute(stackedObject.getWorld(), StackType.fromObject(stackedObject), runnable);
    }

    public static void execute(World world, StackType stackType, Runnable runnable){
        if(isStackThread()) {
            runnable.run();
            return;
        }

        stackServiceWorldMap.computeIfAbsent(world.getName(), stackServiceWorld -> new StackServiceWorld(world.getName())).add(stackType, runnable);
    }

    public static void restart(World world){
        StackServiceWorld stackServiceWorld = stackServiceWorldMap.get(world.getName());
        if(stackServiceWorld != null)
            stackServiceWorld.stop();
        stackServiceWorldMap.put(world.getName(), new StackServiceWorld(world.getName()));
    }

    public static boolean isStackThread(){
        long threadId = Thread.currentThread().getId();
        return stackServiceWorldMap.values().stream().anyMatch(stackServiceWorld -> stackServiceWorld.taskId[0] == threadId || stackServiceWorld.taskId[1] == threadId);
    }

    public static boolean canStackFromThread(){
        return isStackThread() || Bukkit.isPrimaryThread();
    }

    public static void stop(){
        stackServiceWorldMap.values().forEach(StackServiceWorld::stop);
        stackServiceWorldMap.clear();
    }

    private static final class StackServiceWorld {

        @SuppressWarnings("unchecked")
        private final Queue<Runnable>[] asyncRunnables = new ConcurrentLinkedQueue[2];
        private final long[] taskId = new long[] {-1, -1};
        private final Timer[] timers = new Timer[2];

        StackServiceWorld(String world){
            for(StackType stackType : StackType.values())
                timers[stackType.id] = startNewTimer(world, stackType);
        }

        private Timer startNewTimer(String world, StackType type){
            Timer timer = new Timer(true);
            asyncRunnables[type.id] = new ConcurrentLinkedQueue<>();

            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        if (taskId[type.id] == -1) {
                            Thread.currentThread().setName("WildStacker " + type + " Stacking Thread (" + world + ")");
                            taskId[type.id] = Thread.currentThread().getId();
                        }

                        List<Runnable> runnableList = new ArrayList<>(asyncRunnables[type.id]);
                        asyncRunnables[type.id].clear();

                        runnableList.forEach(Runnable::run);
                    }catch(Exception ex){
                        ex.printStackTrace();
                    }
                }
            }, 250, 250);

            return timer;
        }

        void add(StackType type, Runnable runnable){
            asyncRunnables[type.id].add(runnable);
        }

        void stop(){
            for(int i = 0; i < 2; i++) {
                timers[i].cancel();
                asyncRunnables[i].clear();
            }
        }

    }

    public enum StackType{

        ITEMS(0, "Items"),
        ENTITIES(1, "Entities");

        private final int id;
        private final String name;

        StackType(int id, String name){
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public int getId() {
            return id;
        }

        static StackType fromObject(StackedObject stackedObject){
            return stackedObject instanceof StackedItem ? ITEMS : ENTITIES;
        }

    }

}
