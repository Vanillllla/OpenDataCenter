package odc;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Generic bounded BFS. Returns null on overflow, never a partial domain. */
public final class TopologySearch {
    private TopologySearch(){}
    public static <T> Set<T> component(T origin,Function<T,Iterable<T>> neighbors,Predicate<T> allowed,int limit){
        if(limit<1)throw new IllegalArgumentException("limit");
        Set<T> visited=new LinkedHashSet<>();if(!allowed.test(origin))return visited;
        ArrayDeque<T> q=new ArrayDeque<>();q.add(origin);visited.add(origin);
        while(!q.isEmpty())for(T n:neighbors.apply(q.removeFirst())){
            if(visited.contains(n)||!allowed.test(n))continue;
            if(visited.size()>=limit)return null;
            visited.add(n);q.addLast(n);
        }
        return visited;
    }
}
