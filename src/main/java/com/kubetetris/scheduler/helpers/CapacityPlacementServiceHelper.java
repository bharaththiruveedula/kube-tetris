package com.kubetetris.scheduler.helpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.kubetetris.cantor.PairDepair;
import com.kubetetris.scheduler.MigrationPlanDto;
import lombok.extern.slf4j.Slf4j;

import com.kubetetris.Capacity;
import com.kubetetris.Node;
import com.kubetetris.Pod;

@Slf4j
public class CapacityPlacementServiceHelper {

    public Capacity computeRequiredCapacity(Capacity placeCapacity, Node node) {
        Capacity availableCapacity = node.getAvailableCapacity();
        return new Capacity(placeCapacity.getMemoryMB() - availableCapacity.getMemoryMB(),
                placeCapacity.getCpuMillicore() - availableCapacity.getCpuMillicore());
    }

    public List<Node> deepCopy(List<Node> nodes) {
        List<Node> deepCopies = new ArrayList<>();
        nodes.forEach(node -> deepCopies.add(node.clone()));
        return deepCopies;
    }

    public static <K, V extends Comparable<V>> Map<K, V> sortByValues(final Map<K, V> map) {
        Comparator<K> valueComparator = (k1, k2) -> {
            int compare =
                    map.get(k1).compareTo(map.get(k2));
            if (compare == 0)
                return 1;
            else
                return compare;
        };
        Map<K, V> sortedByValues =
                new TreeMap<K, V>(valueComparator);
        sortedByValues.putAll(map);
        return sortedByValues;
    }

    public List<Pod> sort(CopyOnWriteArrayList<Pod> currentMinimum) {
        return currentMinimum.stream().sorted((o1, o2) -> (int) PairDepair
                .pair(o1.getRequest().getCpuMillicore(), o1.getRequest().getMemoryMB() -
                        PairDepair.pair(o2.getRequest().getCpuMillicore(), o2.getRequest().getMemoryMB()))).collect(Collectors.toList());
    }

    public long computeTotalMemoryMB(List<Pod> currentMinimum) {
        return currentMinimum.stream().mapToLong(pod -> pod.getRequest().getMemoryMB()).sum();
    }

    public long computeTotalCpuMilicore(List<Pod> currentMinimum) {
        return currentMinimum.stream().mapToLong(pod -> pod.getRequest().getCpuMillicore()).sum();
    }

    public Pod computeMinimumMigrateablePod(List<Pod> eligiblePods) {
        if (eligiblePods != null && eligiblePods.size() == 1) {
            return eligiblePods.get(0);
        } else if (eligiblePods != null && eligiblePods.size() > 1) {
            return eligiblePods.stream().min((pod1, pod2) -> (int) (pod2.getRequest().getCpuMillicore() - pod2.getRequest().getCpuMillicore())).get();
        }
        return null;
    }

    public List<Pod> computeEligiblePods(Capacity placeCapacity, Capacity requiredCapacity, List<Pod> pods) {
        return pods.stream().map(pod -> {
            Capacity podRequestrequest = pod.getRequest();
            if ((podRequestrequest.getMemoryMB() >= requiredCapacity.getMemoryMB()
                    && podRequestrequest.getCpuMillicore() >= requiredCapacity.getCpuMillicore())
                    && (podRequestrequest.getMemoryMB() < placeCapacity.getMemoryMB()
                    && podRequestrequest.getCpuMillicore() < placeCapacity.getCpuMillicore())) {
                return pod;
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<Pod> computeMultipleEligiblePods(Capacity placeCapacity, List<Pod> pods) {
        return pods.stream().map(pod -> {
            Capacity podRequestrequest = pod.getRequest();
            if (podRequestrequest.getMemoryMB() < placeCapacity.getMemoryMB()
                    && podRequestrequest.getCpuMillicore() < placeCapacity.getCpuMillicore()) {
                return pod;
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public int computeNormalPlacement(Capacity placeCapacity, List<Node> nodes) {
        for (int nodeId = 0; nodeId < nodes.size(); nodeId++) {
            Capacity requiredCapacity = computeRequiredCapacity(placeCapacity, nodes.get(nodeId));
            if (requiredCapacity.getCpuMillicore() <= 0 && requiredCapacity.getMemoryMB() <= 0) {
                return nodeId;
            }
        }
        return -1;
    }

    public List<Integer> computePlacementPriority(Capacity placeCapacity, List<Node> nodes) {
        List<Capacity> requiredCapacity = nodes.stream().map(node -> computeRequiredCapacity(placeCapacity, node)).collect(Collectors.toList
                ());
        Map<Integer, Long> pairedCapacity = new TreeMap<>();
        for (int nodeId = 0; nodeId < requiredCapacity.size(); nodeId++) {
            Capacity capacity = requiredCapacity.get(nodeId);
            log.debug("Required Capacity for node {} is {}", nodeId, capacity);
            Long pairedValue = PairDepair.pair(Math.abs(capacity.getMemoryMB()), Math.abs(capacity.getCpuMillicore()));
            log.debug("Paired value for node {} is {}", nodeId, pairedValue);
            pairedCapacity.put(nodeId, pairedValue);
        }
        Map<Integer, Long> sortedPairedCapacity = sortByValues(pairedCapacity);
        return new ArrayList<>(sortedPairedCapacity.keySet());
    }

    public void printAvailableCapacity(List<Node> nodes, String state) {
        long totalAvailableCpu = nodes.stream().mapToLong(value -> value.getAvailableCapacity().getCpuMillicore()).sum();
        long totalAvailableMem = nodes.stream().mapToLong(value -> value.getAvailableCapacity().getMemoryMB()).sum();
//        log.info("\n\nOverall Available capacity "+state+" placement : memory : {}, cpu : {}\n", totalAvailableMem, totalAvailableCpu);
        System.out.print("\nOverall Available capacity "+state+" placement : memory : "+totalAvailableMem+", cpu : "+totalAvailableCpu);
    }

    public void addAddressToMigrationMoves(Map<Pod, List<Address>> migrationMoves, String from, String to, Pod pod) {
        List<Address> addresses = migrationMoves.get(pod);
        if (addresses != null) {
            addresses.add(new Address(from, to));
        } else {
            addresses = new ArrayList<>();
            addresses.add(new Address(from, to));
        }
        migrationMoves.put(pod, addresses);
    }

    public void addToMigrationPlans(List<MigrationPlanDto> migrationPlans, Map<Pod, List<Address>> migrationMoves) {
        ListIterator<Pod> iterator = new ArrayList(migrationMoves.keySet()).listIterator(migrationMoves.size());
        LinkedList<Pod> eligiblePodsForMigration = new LinkedList<>();
        while (iterator.hasPrevious()) {
            eligiblePodsForMigration.add(iterator.previous());
        }
        for (Pod key : eligiblePodsForMigration) {
            List<Address> addresses = migrationMoves.get(key);
            if (addresses != null && addresses.size() == 2) {
                String from = addresses.get(0).getFrom() == null ? addresses.get(1).getFrom() : addresses.get(0).getFrom();
                String to = addresses.get(0).getTo() == null ? addresses.get(1).getTo() : addresses.get(0).getTo();
                migrationPlans.add(new MigrationPlanDto(key, from, to));
                migrationMoves.remove(key);
            }
        }
        if (migrationMoves != null && migrationMoves.size() == 1) {
            List<Address> workloadAddress = migrationMoves.values().iterator().next();
            Pod workload = migrationMoves.keySet().iterator().next();
            if (workloadAddress != null && workloadAddress.size() == 1) {
                migrationPlans.add(new MigrationPlanDto(workload, workloadAddress.get(0).getFrom(), workloadAddress.get(0).getTo()));
                migrationMoves.remove(workload);
            }
        }
    }

    public void printMigrationPlan(List<MigrationPlanDto> migrationPlans) {
//        log.info("\n\n**************Migration Plan Step By Step**************************\n\n");
        System.out.print("\n\n******************* Migration Plan Step By Step ********************\n");
        for(int migrationCount = 0; migrationCount < migrationPlans.size(); migrationCount++) {
            MigrationPlanDto migrationPlan = migrationPlans.get(migrationCount);
            if (migrationPlan.getFromNode() == null) {
//                log.info("\n\n{}. Place workload capacity {} to node: {}\n\n", migrationCount+1,  migrationPlan.getPod().getRequest(),
//                        migrationPlan.getToNode());
                System.out.print("\n"+(migrationCount+1)+". Place workload capacity "+migrationPlan.getPod().getRequest()+" to node: " +
                        ""+migrationPlan.getToNode()+"\n\n\n\n\n");
            } else {
//                log.info("\n\n{}. Move pod {} from node: {}  to node: {}\n\n",migrationCount+1, migrationPlan.getPod().getName(),
//                        migrationPlan.getFromNode(), migrationPlan.getToNode());

                System.out.print("\n"+(migrationCount+1)+". Move Pod "+migrationPlan.getPod().getName()+" from node: "+migrationPlan.getFromNode
                        ()+" to node:"+migrationPlan.getToNode());

            }
        }
    }
}
