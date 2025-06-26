package me.adabugra.pl3xmapfoliaregions;

import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickData;
import io.papermc.paper.threadedregions.TickRegions;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.adabugra.pl3xmapfoliaregions.utils.CoordinateUtils;
import net.minecraft.world.level.ChunkPos;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

@SuppressWarnings("SpellCheckingInspection")
public final class Pl3xMapFoliaRegions extends JavaPlugin {
    private final int sectionSize = 1 << TickRegions.getRegionChunkShift();
    private final Map<String, ScheduledTask> tasks = new HashMap<>();

    private static @NotNull String getDetail(TickData.TickReportData reportData, List<Long> sections, TickRegions.RegionStats stats) {
        final TickData.SegmentData tpsData = reportData.tpsData().segmentAll();
        final double mspt = reportData.timePerTickData().segmentAll().average() / 1.0E6;

        return "Sections: " + sections.size() + "<br>" +
                "Chunks: " + stats.getChunkCount() + "<br>" +
                "Entities: " + stats.getEntityCount() + "<br>" +
                "Players: " + stats.getPlayerCount() + "<br>" +
                "TPS: " + String.format("%.2f", tpsData.average()) + "<br>" +
                "MSPT: " + String.format("%.2f", mspt);
    }

    private static List<Point> getPolygonPoints(List<Long> chunkPositions) {
        List<Point> points = new ArrayList<>();
        for (long chunkPos : chunkPositions) {
            int x = CoordinateUtils.getChunkX(chunkPos);
            int z = CoordinateUtils.getChunkZ(chunkPos);
            points.add(Point.of(x * 16, z * 16));
            points.add(Point.of(x * 16 + 15, z * 16));
            points.add(Point.of(x * 16, z * 16 + 15));
            points.add(Point.of(x * 16 + 15, z * 16 + 15));
        }
        return convexHull(points);
    }

    private static List<Point> convexHull(List<Point> points) {
        if (points.size() < 3) {
            return points;
        }

        HashSet<Point> uniquePoints = new HashSet<>(points);
        points = uniquePoints.stream().sorted((p1, p2) -> {
            if (p1.x() != p2.x()) {
                return p1.x() - p2.x();
            }
            return p1.z() - p2.z();
        }).toList();

        List<Point> upperHull = new ArrayList<>();
        List<Point> lowerHull = new ArrayList<>();

        for (Point p : points) {
            while (upperHull.size() >= 2 &&
                    crossProduct(upperHull.get(upperHull.size() - 2), upperHull.getLast(), p) >= 0) {
                upperHull.removeLast();
            }
            upperHull.add(p);
        }

        for (int i = points.size() - 1; i >= 0; i--) {
            Point p = points.get(i);
            while (lowerHull.size() >= 2 &&
                    crossProduct(lowerHull.get(lowerHull.size() - 2), lowerHull.getLast(), p) >= 0) {
                lowerHull.removeLast();
            }
            lowerHull.add(p);
        }

        upperHull.removeLast();
        lowerHull.removeLast();

        upperHull.addAll(lowerHull);

        Point lastPoint = null;
        upperHull.add(upperHull.getFirst());

        List<Point> result = new ArrayList<>();
        for (Point point : upperHull) {
            if (lastPoint != null && lastPoint.x() != point.x() && lastPoint.z() != point.z()) {
                Point candidate1 = Point.of(lastPoint.x(), point.z());
                if (uniquePoints.contains(candidate1)) {
                    result.add(candidate1);
                } else {
                    Point candidate2 = Point.of(point.x(), lastPoint.z());
                    if (uniquePoints.contains(candidate2)) {
                        result.add(candidate2);
                    }
                }
            }

            lastPoint = point;
            result.add(point);
        }
        result.removeLast();
        return result;
    }

    private static double crossProduct(Point a, Point b, Point c) {
        return (b.x() - a.x()) * (c.z() - a.z()) - (b.z() - a.z()) * (c.x() - a.x());
    }

    @Override
    public void onEnable() {
        for (World world : Pl3xMap.api().getWorldRegistry()) {
            ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task1 -> updateRegionMarkers(world), 20, 20 * 5);
            this.tasks.put(world.getName(), task);
        }
    }

    @Override
    public void onDisable() {
        for (ScheduledTask value : this.tasks.values()) {
            value.cancel();
        }
        this.tasks.clear();

        for (World world : Pl3xMap.api().getWorldRegistry()) {
            world.getLayerRegistry().unregister("folia-regions");
        }
    }

    private void updateRegionMarkers(World pl3xWorld) {
        org.bukkit.World bukkitWorld = Bukkit.getWorld(pl3xWorld.getName());
        if (bukkitWorld == null) {
            getLogger().warning("Bukkit world not found: " + pl3xWorld.getName());
            return;
        }

        ThreadedRegionizer<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> regioniser = ((CraftWorld) bukkitWorld).getHandle().regioniser;

        // Remove existing layer if it exists
        pl3xWorld.getLayerRegistry().unregister("folia-regions");

        SimpleLayer layer = new SimpleLayer("folia-regions", () -> "Folia Regions");
        layer.setDefaultHidden(true);

        // Add markers
        Map<String, Marker<?>> markers = createMarkers(regioniser);
        for (Map.Entry<String, Marker<?>> entry : markers.entrySet()) {
            layer.addMarker(entry.getValue());
        }

        // Register the layer
        pl3xWorld.getLayerRegistry().register(layer);
    }

    public Map<String, Marker<?>> createMarkers(ThreadedRegionizer<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> regioniser) {
        Map<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>, List<Long>> regionSections = new HashMap<>();
        regioniser.computeForAllRegions((region) -> regionSections.put(region, region.getOwnedSections()));

        Map<String, Marker<?>> markers = new HashMap<>();
        for (Map.Entry<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>, List<Long>> entry : regionSections.entrySet()) {
            ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = entry.getKey();
            List<Long> sections = entry.getValue();

            ChunkPos centerChunk = region.getCenterChunk();
            if (centerChunk == null) {
                continue; // dead region, with an empty chunk list
            }
            String label = "Region@" + region.getData().world.getTypeKey().location().getPath() + "[" + centerChunk.x + "," + centerChunk.z + "]";

            List<Point> points = getSectionPoints(sections);

            TickRegions.RegionStats stats = region.getData().getRegionStats();
            final TickData.TickReportData reportData = region.getData().getRegionSchedulingHandle().getTickReport5s(System.nanoTime());
            String detail;
            if (reportData != null) {
                detail = getDetail(reportData, sections, stats);
            } else {
                detail = "Sections: " + sections.size() + "<br>" +
                        "Chunks: " + stats.getChunkCount() + "<br>" +
                        "Entities: " + stats.getEntityCount() + "<br>" +
                        "Players: " + stats.getPlayerCount() + "<br>" +
                        "TPS: N/A<br>" +
                        "MSPT: N/A";
            }

            var strokeColor = new Color(255, 0, 0);
            var fillColor = new Color(255, 0, 0, 70);

            Polyline polyline = new Polyline(label + "_polyline", points);
            List<Polyline> polylines = List.of(polyline);

            var polyMarker = new net.pl3x.map.core.markers.marker.Polygon(label, polylines);
            polyMarker.setOptions(Options.builder()
                    .strokeColor(strokeColor.getRGB())
                    .strokeWeight(2)
                    .fillColor(fillColor.getRGB())
                    .popupContent(detail)
                    .build());

            markers.put(label, polyMarker);
        }
        return markers;
    }

    private List<Point> getSectionPoints(List<Long> sections) {
        List<Point> points = getPolygonPoints(sections);
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            Point newPoint = Point.of(point.x() * sectionSize, point.z() * sectionSize);
            points.set(i, newPoint);
        }
        return points;
    }
}