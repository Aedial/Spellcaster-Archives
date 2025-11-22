package com.spellarchives.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.spellarchives.gui.GuiSpellArchive;
import com.spellarchives.gui.SpellPresentation;

/**
 * A small per-GUI cache manager for layout and presentation caches used by GuiSpellArchive.
 * It keeps simple cached values and invalidates them when the GuiStyle.CONFIG_REVISION changes
 * or when the snapshot keys / grid columns change.
 */
public final class GuiCacheManager {
    private Set<String> lastKeys = new HashSet<>();
    private GuiSpellArchive.DisplayRows cachedDisplayRows = null;
    private List<GuiSpellArchive.PageInfo> allCachedPages = null;

    // GUI instance helpers
    private GuiSpellArchive.BookEntry hoveredEntry = null;
    private SpellPresentation cachedPresentationObj = null;
    private String cachedPresentationKey = null;
    private GuiSpellArchive.GridGeometry cachedGG = null;

    private int cachedGridColsForDisplay = -1;

    // Geometry cache
    private int lastGridX = -1, lastGridY = -1, lastGridW = -1, lastGridH = -1;
    private int lastHeaderH = -1, lastCellH = -1, lastRowGap = -1, lastGridRows = -1;

    private int cachedStyleRevision = -1;

    public GuiCacheManager() {}

    public void checkStyleRevision(int styleRevision) {
        if (this.cachedStyleRevision != styleRevision) {
            clearAll();

            this.cachedStyleRevision = styleRevision;
        }
    }

    public void clearAll() {
        lastKeys.clear();
        cachedDisplayRows = null;
        allCachedPages = null;
        cachedGridColsForDisplay = -1;
        hoveredEntry = null;
        cachedPresentationObj = null;
        cachedGG = null;

        lastGridX = -1; lastGridY = -1; lastGridW = -1; lastGridH = -1;
        lastHeaderH = -1; lastCellH = -1; lastRowGap = -1; lastGridRows = -1;
    }

    public void setHoveredEntry(GuiSpellArchive.BookEntry e) {
        this.hoveredEntry = e;
    }

    public GuiSpellArchive.BookEntry getHoveredEntry() {
        return this.hoveredEntry;
    }

    public SpellPresentation getCachedPresentation(String key, int count) {
        if (this.cachedPresentationObj == null) return null;

        // simple match by equality on previously cached key/counter encoded in headerName and count
        // We store presentation only for the current hovered; if mismatch return null
        if (this.cachedPresentationKey != null && this.cachedPresentationKey.equals(key) && this.cachedPresentationObj.count == count) return this.cachedPresentationObj;

        return null;
    }

    public void putCachedPresentation(String key, SpellPresentation p) {
        this.cachedPresentationKey = key;
        this.cachedPresentationObj = p;
    }

    public void clearCachedPresentation() {
        this.cachedPresentationObj = null;
        this.cachedPresentationKey = null;
    }

    public GuiSpellArchive.GridGeometry getCachedGG() {
        return this.cachedGG;
    }

    public void setCachedGG(GuiSpellArchive.GridGeometry gg) {
        this.cachedGG = gg;
    }

    public boolean haveKeysChanged(Set<String> keys) {
        return !keys.equals(this.lastKeys);
    }

    public boolean haveColsChanged(int gridCols) {
        return this.cachedGridColsForDisplay != gridCols;
    }

    public GuiSpellArchive.DisplayRows getOrBuildDisplayRows(Set<String> snapshotKeys, Map<Integer, List<GuiSpellArchive.BookEntry>> rowsByTier, int gridCols) {
        boolean keysChanged = !snapshotKeys.equals(this.lastKeys);
        boolean colsChanged = (this.cachedGridColsForDisplay != gridCols);

        if (this.cachedDisplayRows == null || keysChanged || colsChanged) {
            // rebuild
            List<List<GuiSpellArchive.BookEntry>> displayRows = new ArrayList<>();
            List<Integer> displayRowTiers = new ArrayList<>();

            for (Integer tierKey : rowsByTier.keySet()) {
                List<GuiSpellArchive.BookEntry> rowBooks = rowsByTier.get(tierKey);
                if (rowBooks == null || rowBooks.isEmpty()) continue;

                for (int off = 0; off < rowBooks.size(); off += gridCols) {
                    int endIdx = Math.min(off + gridCols, rowBooks.size());
                    displayRows.add(rowBooks.subList(off, endIdx));
                    displayRowTiers.add(tierKey);
                }
            }

            this.cachedDisplayRows = new GuiSpellArchive.DisplayRows(displayRows, displayRowTiers);
            this.cachedGridColsForDisplay = gridCols;
            this.allCachedPages = null; // page info depends on displayRows
            this.lastKeys = new HashSet<>(snapshotKeys);
        }

        return this.cachedDisplayRows;
    }

    public GuiSpellArchive.PageInfo getOrBuildPageInfo(GuiSpellArchive.DisplayRows dr,
                                                       int gridX, int gridY, int gridW, int gridH, int headerH,
                                                       int page, int cellH, int rowGap, int gridRows) {
        boolean geometryChanged =
            lastGridX != gridX || lastGridY != gridY || lastGridW != gridW || lastGridH != gridH ||
            lastHeaderH != headerH || lastCellH != cellH || lastRowGap != rowGap || lastGridRows != gridRows;

        if (this.allCachedPages == null || geometryChanged) {
            // Rebuild all pages
            this.allCachedPages = new ArrayList<>();

            int totalRows = dr.rows.size();
            int currentRow = 0;

            while (currentRow < totalRows) {
                List<GuiSpellArchive.GrooveRow> pageLayout = new ArrayList<>();
                int curY = gridY + headerH;
                int gridBottom = gridY + gridH;
                Set<Integer> pageSeenTiers = new HashSet<>();

                while (currentRow < totalRows) {
                    int tier = dr.rowTiers.get(currentRow);
                    boolean showHeader = !pageSeenTiers.contains(tier);

                    if (curY + cellH > gridBottom) break;

                    pageLayout.add(new GuiSpellArchive.GrooveRow(currentRow, tier, curY, showHeader));
                    pageSeenTiers.add(tier);

                    if (currentRow + 1 < totalRows) {
                        int nextTier = dr.rowTiers.get(currentRow + 1);
                        boolean nextIsFirstOfTier = !pageSeenTiers.contains(nextTier);
                        curY += cellH + (nextIsFirstOfTier ? headerH + rowGap : 1);
                    }

                    currentRow++;
                }

                // Safety break to avoid infinite loop if a single row doesn't fit
                if (pageLayout.isEmpty() && currentRow < totalRows) break;

                boolean hasNext = currentRow < totalRows;
                this.allCachedPages.add(new GuiSpellArchive.PageInfo(pageLayout, hasNext));
            }

            // Update cache keys
            lastGridX = gridX; lastGridY = gridY; lastGridW = gridW; lastGridH = gridH;
            lastHeaderH = headerH; lastCellH = cellH; lastRowGap = rowGap; lastGridRows = gridRows;
        }

        if (page >= 0 && page < this.allCachedPages.size()) return this.allCachedPages.get(page);

        return new GuiSpellArchive.PageInfo(new ArrayList<>(), false);
    }
}
