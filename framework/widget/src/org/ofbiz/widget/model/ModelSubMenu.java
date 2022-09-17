/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.widget.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.collections.MapStack;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.widget.WidgetWorker;
import org.ofbiz.widget.model.ModelMenu.CurrentMenuDefBuildArgs;
import org.ofbiz.widget.model.ModelMenu.GeneralBuildArgs;
import org.ofbiz.widget.model.ModelMenu.MenuAndItemLookup;
import org.ofbiz.widget.model.ModelMenuItem.ModelMenuItemAlias;
import org.ofbiz.widget.renderer.MenuStringRenderer;
import org.w3c.dom.Element;

/**
 * SCIPIO: Models a sub-menu entry under menu-item.
 * <p>
 * TODO: Merge constructor
 */
@SuppressWarnings("serial")
public class ModelSubMenu extends ModelMenuCommon { // SCIPIO: new comon base class to share with top menu

    //private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    private final boolean anonName; // true if an anonymous name was generated for this menu
    private final String effectiveName;

    private final List<ModelAction> actions;
    private final List<ModelMenuItem> menuItemList;
    private final Map<String, ModelMenuItem> menuItemMap;
    private final List<ModelMenuItem> extraMenuItemList;
    private final ModelMenuItem parentMenuItem; // SCIPIO: WARN: must be fixed-up after construct due to the way the items are merged

    private final FlexibleStringExpander id;
    private final FlexibleStringExpander style;
    private final FlexibleStringExpander title;
    private final ModelMenu model; // SCIPIO: NOTE: this is the logical "model" for this sub-menu, different from the Top model menu reference! (getTopMenu)
    private final String modelScope;
    private transient ModelMenu styleModelMenu = null; // SCIPIO: records which model menu should be used for style fields (NOTE: doesn't need synchronizing)
    private transient ModelMenu funcModelMenu = null; // SCIPIO: records which model menu should be used for functional fields

    private final String itemsSortMode;

    private final FlexibleStringExpander shareScope; // SCIPIO: NOTE: 2016-11-02: default is now TRUE

    private final FlexibleStringExpander expanded; // 2016-11-11: expansion override
    private final FlexibleStringExpander selected; // 2016-11-11: selected override
    private final FlexibleStringExpander disabled; // 2016-11-11: disabled override

    private final Map<String, ModelMenuItemAlias> menuItemAliasMap;
    private final Map<String, String> menuItemNameAliasMap; // for optimized simple-hidden item alias lookups

    private transient Set<String> simpleStyleNames = null;

    public ModelSubMenu(Element subMenuElement, String currResource, ModelMenuItem parentMenuItem, BuildArgs buildArgs) {
        super(subMenuElement);
        buildArgs.genBuildArgs.totalSubMenuCount++;
        ModelMenu topMenu = parentMenuItem.getTopMenu();
        List<? extends Element> extraMenuItems = buildArgs.extraMenuItems;
        this.parentMenuItem = parentMenuItem;

        String modelAddress = subMenuElement.getAttribute("model");
        String modelScope = subMenuElement.getAttribute("model-scope");
        ModelMenu model = null;

        // check the helper include attribute
        String includeAddress = subMenuElement.getAttribute("include");
        Element includeElement = null;
        String includeName = null;
        if (!includeAddress.isEmpty()) {
            if (modelAddress.isEmpty()) {
                // set this as the model
                modelAddress = includeAddress;
            }
            if (modelScope.isEmpty()) {
                modelScope = buildArgs.currentMenuDefBuildArgs.codeBehavior.defaultSubMenuInstanceScope;
                if (modelScope == null || modelScope.isEmpty()) {
                    modelScope = "full";
                }
            }

            ModelLocation menuLoc = ModelLocation.fromAddress(includeAddress);

            // create an extra include element
            includeElement = subMenuElement.getOwnerDocument().createElement("include-elements");
            includeElement.setAttribute("menu-name", menuLoc.getName());
            if (menuLoc.hasResource()) {
                includeElement.setAttribute("resource", menuLoc.getResource());
            }
            includeElement.setAttribute("recursive", "full");
            includeElement.setAttribute("is-sub-menu-model-entry", "true"); // special flag to distinguish this entry
            includeName = menuLoc.getName();
        }

        if (!modelAddress.isEmpty()) {
            ModelLocation menuLoc = ModelLocation.fromAddress(modelAddress);
            model = ModelMenu.getMenuDefinition(menuLoc.getResource(), menuLoc.getName(),
                    subMenuElement, buildArgs.genBuildArgs); // topModelMenu.getMenuLocation()
        }

        // figure out our name
        String effectiveName = getName();
        boolean anonName = false;
        if (effectiveName == null || effectiveName.isEmpty()) {
            // from-include is the default, for convenience
            String autoSubMenuNames = buildArgs.currentMenuDefBuildArgs.codeBehavior.autoSubMenuNames;
            if (UtilValidate.isEmpty(autoSubMenuNames) || "from-include".equals(autoSubMenuNames)) {
                effectiveName = includeName;
            }
            if (effectiveName == null || effectiveName.isEmpty()) {
                effectiveName = makeAnonName(buildArgs);
                anonName = true;
            }
        }
        this.anonName = anonName;
        this.effectiveName = effectiveName;

        if (modelScope == null || modelScope.isEmpty()) {
            modelScope = buildArgs.currentMenuDefBuildArgs.codeBehavior.defaultSubMenuModelScope;
            if (modelScope == null || modelScope.isEmpty()) {
                modelScope = "full";
            }
        }

        // override
        if (buildArgs.forceSubMenuModelScope != null && !buildArgs.forceSubMenuModelScope.isEmpty()) {
            modelScope = buildArgs.forceSubMenuModelScope;
        }

        // set these early
        this.model = model;
        this.modelScope = modelScope;

        // support included sub-menu items
        List<Element> preInclElements = null;
        if (includeElement != null) {
            preInclElements = new ArrayList<>();
            preInclElements.add(includeElement);
        }

        // include actions
        ArrayList<ModelAction> actions = new ArrayList<ModelAction>();
        topMenu.processIncludeActions(subMenuElement, preInclElements, null, actions,
                currResource, true,
                buildArgs.currentMenuDefBuildArgs, buildArgs.genBuildArgs);
        actions.trimToSize();
        this.actions = Collections.unmodifiableList(actions);

        // include items
        ArrayList<ModelMenuItem> menuItemList = new ArrayList<>();
        Map<String, ModelMenuItem> menuItemMap = new HashMap<>();
        Map<String, ModelMenuItemAlias> menuItemAliasMap = new HashMap<>();
        topMenu.processIncludeMenuItems(subMenuElement, preInclElements, null, menuItemList, menuItemMap,
                menuItemAliasMap, true,
                currResource, true, null, null, buildArgs.forceSubMenuModelScope, this,
                buildArgs.currentMenuDefBuildArgs, buildArgs.genBuildArgs);

        // extra items: replaces the legacy menu-item load originally in ModelMenuItem constructor
        if (extraMenuItems != null && !extraMenuItems.isEmpty()) {
            // extra maps just to record the originals
            ArrayList<ModelMenuItem> extraMenuItemList = new ArrayList<ModelMenuItem>();
            Map<String, ModelMenuItem> extraMenuItemMap = new HashMap<String, ModelMenuItem>();

            ModelMenuItem.BuildArgs itemBuildArgs = new ModelMenuItem.BuildArgs(buildArgs);

            for (Element itemElement : extraMenuItems) {
                ModelMenuItem modelMenuItem = new ModelMenuItem(itemElement, this, itemBuildArgs);
                topMenu.addUpdateMenuItem(modelMenuItem, menuItemList, menuItemMap, itemBuildArgs);
                topMenu.addUpdateMenuItem(modelMenuItem, extraMenuItemList, extraMenuItemMap, itemBuildArgs);
            }

            extraMenuItemList.trimToSize();
            this.extraMenuItemList = Collections.unmodifiableList(extraMenuItemList);
        } else {
            this.extraMenuItemList = Collections.emptyList();
        }

        menuItemList.trimToSize();
        this.menuItemList = Collections.unmodifiableList(menuItemList);
        this.menuItemMap = Collections.unmodifiableMap(menuItemMap);

        this.menuItemAliasMap = Collections.unmodifiableMap(menuItemAliasMap);
        this.menuItemNameAliasMap = ModelMenu.makeMenuItemNameAliasMap(menuItemAliasMap);

        this.id = FlexibleStringExpander.getInstance(subMenuElement.getAttribute("id"));
        this.style = FlexibleStringExpander.getInstance(subMenuElement.getAttribute("style"));
        this.title = FlexibleStringExpander.getInstance(subMenuElement.getAttribute("title"));

        this.itemsSortMode = subMenuElement.getAttribute("items-sort-mode");
        this.shareScope = FlexibleStringExpander.getInstance(subMenuElement.getAttribute("share-scope"));
        this.expanded = FlexibleStringExpander.getInstance(subMenuElement.getAttribute("expanded"));
        this.selected = FlexibleStringExpander.getInstance(subMenuElement.getAttribute("selected"));
        this.disabled = FlexibleStringExpander.getInstance(subMenuElement.getAttribute("disabled"));
    }

    // SCIPIO: copy constructor
    private ModelSubMenu(ModelSubMenu existing,
            ModelMenu modelMenu, ModelMenuItem parentMenuItem, BuildArgs buildArgs) {
        super(existing.getName());
        buildArgs.genBuildArgs.totalSubMenuCount++;
        this.anonName = existing.anonName;
        if (this.anonName) {
            this.effectiveName = makeAnonName(buildArgs);
        } else {
            this.effectiveName = existing.effectiveName;
        }
        this.parentMenuItem = parentMenuItem != null ? parentMenuItem : existing.parentMenuItem;

        ArrayList<ModelAction> actions = new ArrayList<>(existing.actions);
        actions.trimToSize();
        this.actions = Collections.unmodifiableList(actions);
        ArrayList<ModelMenuItem> menuItemList = new ArrayList<>();
        Map<String, ModelMenuItem> menuItemMap = new HashMap<>();
        ModelMenuItem.cloneModelMenuItems(existing.getMenuItemList(),
                menuItemList, menuItemMap, getTopMenu(), this,
                new ModelMenuItem.BuildArgs(buildArgs));
        menuItemList.trimToSize();
        this.menuItemList = Collections.unmodifiableList(menuItemList);
        this.menuItemMap = Collections.unmodifiableMap(menuItemMap);

        ArrayList<ModelMenuItem> extraMenuItemList = new ArrayList<>();
        for(ModelMenuItem menuItem : existing.extraMenuItemList) {
            extraMenuItemList.add(menuItemMap.get(menuItem.getName()));
        }
        extraMenuItemList.trimToSize();
        this.extraMenuItemList = Collections.unmodifiableList(extraMenuItemList);

        this.id = existing.id;
        this.style = existing.style;
        this.title = existing.title;
        this.model = existing.model;
        this.modelScope = UtilValidate.isNotEmpty(buildArgs.forceSubMenuModelScope) ? buildArgs.forceSubMenuModelScope : existing.modelScope;

        this.itemsSortMode = existing.itemsSortMode;
        this.shareScope = existing.shareScope;
        this.expanded = existing.expanded;
        this.selected = existing.selected;
        this.disabled = existing.disabled;

        Map<String, ModelMenuItemAlias> menuItemAliasMap = new HashMap<>();
        ModelMenuItemAlias.cloneModelMenuItemAliases(existing.menuItemAliasMap, menuItemAliasMap, getTopMenu(), this,
                new ModelMenuItem.BuildArgs(buildArgs));
        this.menuItemAliasMap = Collections.unmodifiableMap(menuItemAliasMap);
        this.menuItemNameAliasMap = ModelMenu.makeMenuItemNameAliasMap(menuItemAliasMap);
    }

    public static Map<String, String> makeSpecialMenuItemNameMap(Set<String> menuItemNamesAsParent, Set<String> menuItemNamesAsParentNoSub) {
        Map<String, String> map = new HashMap<>();
        if (UtilValidate.isNotEmpty(menuItemNamesAsParent)) {
            for(String name : menuItemNamesAsParent) {
                map.put(name, "PARENT");
            }
        }
        if (UtilValidate.isNotEmpty(menuItemNamesAsParentNoSub)) {
            for(String name : menuItemNamesAsParentNoSub) {
                map.put(name, "PARENT-NOSUB");
            }
        }
        return map;
    }

    String makeAnonName(BuildArgs buildArgs) {
        String defaultName;
        defaultName = "_submenu_" + buildArgs.genBuildArgs.totalSubMenuCount;
        return defaultName;
    }

    /**
     * SCIPIO: Clones item.
     * <p>
     * NOTE: if modelMenu/parentMenuItem are null, they are taken from the current item.
     */
    public ModelSubMenu cloneModelSubMenu(ModelMenu modelMenu, ModelMenuItem parentMenuItem, BuildArgs buildArgs) {
        return new ModelSubMenu(this, modelMenu, parentMenuItem, buildArgs);
    }

    public static void cloneModelSubMenus(List<ModelSubMenu> subMenuList,
            List<ModelSubMenu> targetList, Map<String, ModelSubMenu> targetMap,
            ModelMenu modelMenu, ModelMenuItem parentMenuItem, BuildArgs buildArgs) {
        for(ModelSubMenu subMenu : subMenuList) {
            ModelSubMenu clonedSubMenu = subMenu.cloneModelSubMenu(modelMenu, parentMenuItem, buildArgs);
            targetList.add(clonedSubMenu);
            targetMap.put(clonedSubMenu.getName(), clonedSubMenu);
        }
    }

    /**
     * SCIPIO: Returns the alt model menu for this item's CHILDREN.
     */
    public ModelMenu getModel() {
        return this.model;
    }

    public String getModelScope() {
        return this.modelScope;
    }

    public boolean isModelStyleScope() {
        return "full".equals(modelScope) || (!"none".equals(modelScope) && !"func".equals(modelScope));
    }

    public boolean isModelFuncScope() {
        return "full".equals(modelScope) || "func".equals(modelScope);
    }

    public String getId(Map<String, Object> context) {
        return this.id.expandString(context);
    }

    public String getStyle(Map<String, Object> context) {
        String style = getStyle();
        return FlexibleStringExpander.expandString(style, context).trim();
    }

    public String getStyle() {
        String subMenuModelStyle = "";
        if (model != null && isModelStyleScope()) {
            subMenuModelStyle = model.getMenuContainerStyle();
        }
        return getStyle("subMenuStyle", this.style.getOriginal(), subMenuModelStyle);
    }

    /**
     * WARN: returns unordered
     */
    public Set<String> getSimpleStyleNames() {
        // WARN: special thread-safety pattern in place (unmodifiableSet important!)
        Set<String> styleNames = this.simpleStyleNames;
        if (styleNames == null) {
            styleNames = Collections.unmodifiableSet(WidgetWorker.extractSimpleStyles(getStyle(), new HashSet<String>()));
            this.simpleStyleNames = styleNames;
        }
        return styleNames;
    }

    public boolean hasSimpleStyleName(String name) {
        return getSimpleStyleNames().contains(name);
    }

    public String getTitle(Map<String, Object> context) {
        return this.title.expandString(context);
    }

    public List<ModelMenuItem> getMenuItemList() {
        return this.menuItemList;
    }

    public Map<String, ModelMenuItem> getMenuItemMap() {
        return menuItemMap;
    }

    public List<ModelMenuItem> getExtraMenuItemList() {
        return this.extraMenuItemList;
    }

    public String getEffectiveName() {
        return this.effectiveName;
    }

    public boolean isAnonName() {
        return this.anonName;
    }

    public ModelMenuItem getMenuItemByName(String name) {
        return this.menuItemMap.get(name);
    }

    public ModelMenuItem getMenuItemByNameMapped(String name) {
        return this.menuItemMap.get(getMappedMenuItemName(name));
    }

    @Deprecated
    public MenuAndItemLookup resolveMenuAndItemByTrail(String[] nameList) {
        return resolveMenuAndItemByTrail(nameList[0], nameList, 1);
    }

    @Deprecated
    public MenuAndItemLookup resolveMenuAndItemByTrail(String name, String[] nameList, int nextNameIndex) {
        ModelMenuItem currLevelItem = this.menuItemMap.get(name);
        if (nextNameIndex >= nameList.length) {
            return new MenuAndItemLookup(currLevelItem);
        } else if (currLevelItem != null) {
            return currLevelItem.resolveMenuAndItemByTrail(nameList[nextNameIndex], nameList, nextNameIndex + 1);
        } else {
            return null;
        }
    }

    /**
     * SCIPIO: Gets style.
     * <p>
     * TODO?: this could probably cache based on passed name for faster access, but not certain
     * if safe. OR it could be integrated into constructor,
     * but I'm not sure that safe either (because of the "link" element).
     */
    String getStyle(String name, String style, String defaultStyle) {
        return ModelMenu.buildStyle(style, null, defaultStyle);
    }

    public List<ModelAction> getActions() {
        return actions;
    }

    /**
     * Returns the top-level menu model element. Not to be confused with the
     * sub-menu's logical "model".
     */
    public ModelMenu getTopMenu() {
        return getParentMenuItem().getTopMenu();
    }

    public ModelMenuItem getParentMenuItem() {
        return parentMenuItem;
    }

    public ModelMenuItem getTopAncestorMenuItem() {
        return getParentMenuItem().getTopAncestorMenuItem();
    }

    /**
     * SCIPIO: Returns THIS item's alt model menu for style fields.
     * <p>
     * DEV NOTE: Can only be called only after the ModelMenu is fully constructed.
     */
    public ModelMenu getStyleModelMenu() {
        // WARN: special fast thread-safe read pattern in use here (single atomic read of instance variable (which is immutable object)
        // using local variable); no synchronized block used because single calculation/assignment not important.
        ModelMenu result = this.styleModelMenu;
        if (result == null) {
            result = this.parentMenuItem.getStyleModelMenu();
            if (model != null && isModelStyleScope()) {
                styleModelMenu = model;
            }
            this.styleModelMenu = result;
        }
        return result;
    }

    public ModelMenu getImmediateStyleModelMenu() {
        return isModelStyleScope() ? this.model : null;
    }

    public boolean hasImmediateStyleModelMenu() {
        return getImmediateStyleModelMenu() != null;
    }

    /**
     * SCIPIO: Returns THIS item's alt model menu for functional/logic fields.
     * <p>
     * DEV NOTE: Can only be called only after the ModelMenu is fully constructed.
     */
    public ModelMenu getFuncModelMenu() {
        // WARN: special fast thread-safe read pattern in use here (single atomic read of instance variable (which is immutable object)
        // using local variable); no synchronized block used because single calculation/assignment not important.
        ModelMenu result = this.funcModelMenu;
        if (result == null) {
            result = this.parentMenuItem.getFuncModelMenu();
            if (model != null && isModelFuncScope()) {
                result = model;
            }
            this.funcModelMenu = result;
        }
        return result;
    }

    public ModelMenu getImmediateFuncModelMenu() {
        return isModelFuncScope() ? this.model : null;
    }

    public boolean hasImmediateFuncModelMenu() {
        return getImmediateFuncModelMenu() != null;
    }

    public String getItemsSortMode() {
        if (!this.itemsSortMode.isEmpty()) {
            return this.itemsSortMode;
        } else if (getModel() != null && isModelFuncScope()) {
            return getModel().getItemsSortMode();
        } else {
            return "";
        }
    }

    public String getOrigItemsSortMode() {
        return this.itemsSortMode;
    }

    public List<ModelMenuItem> getOrderedMenuItemList(final Map<String, Object> context) {
        return ModelMenu.getOrderedMenuItemList(context, getItemsSortMode(), getMenuItemList());
    }

    public boolean shareScope(Map<String, Object> context) {
        String shareScopeString = this.shareScope.expandString(context);
        return "true".equals(shareScopeString); // NOTE: 2016-11-02: default is now TRUE
    }

    public FlexibleStringExpander getExpandedExdr() {
        return expanded;
    }

    public Boolean getExpand(Map<String, Object> context) {
        return UtilMisc.booleanValue(this.expanded.expandString(context));
    }

    public void renderSubMenuString(Appendable writer, Map<String, Object> context, MenuStringRenderer menuStringRenderer)
            throws IOException {

        boolean protectScope = !shareScope(context);
        if (protectScope) {
            if (!(context instanceof MapStack<?>)) {
                context = MapStack.create(context);
            }
            UtilGenerics.<MapStack<String>>cast(context).push();
        }

        // NOTE: there is no stack push/pop here!
        AbstractModelAction.runSubActions(actions, context);

        // render menu open
        menuStringRenderer.renderSubMenuOpen(writer, context, this);

        // render each menuItem row, except hidden & ignored rows
        for (ModelMenuItem item : this.getOrderedMenuItemList(context)) {
            item.renderMenuItemString(writer, context, menuStringRenderer);
        }

        // render menu close
        menuStringRenderer.renderSubMenuClose(writer, context, this);

        if (protectScope) {
            UtilGenerics.<MapStack<String>>cast(context).pop();
        }
    }

    /**
     * SCIPIO: NOTE: only valid if the sub-menus were part of the same ModelMenu instance.
     */
    public boolean isSame(ModelSubMenu subMenu) {
        return (this == subMenu); // SCIPIO: NOTE: this works because we know how the ModelMenu was built
    }

    public boolean isParentOf(ModelMenuItem menuItem) {
        if (menuItem == null) {
            return false;
        }
        return menuItem.isSame(menuItemMap.get(menuItem.getName()));
    }

    public boolean isChildOf(ModelMenuItem menuItem) {
        return getParentMenuItem().isSame(menuItem);
    }

    public boolean isAncestorOf(ModelMenuItem menuItem) {
        if (menuItem == null) {
            return false;
        } else {
            return isSameOrAncestorOf(menuItem.getParentSubMenu());
        }
    }

    public boolean isSameOrAncestorOf(ModelSubMenu subMenu) {
        if (subMenu == null) {
            return false;
        }
        else if (isSame(subMenu)) {
            return true;
        } else {
            return isSameOrAncestorOf(subMenu.getParentMenuItem().getParentSubMenu());
        }
    }

    public Map<String, ModelMenuItemAlias> getMenuItemAliasMap() { // SCIPIO: new
        return menuItemAliasMap;
    }

    public String getMappedMenuItemName(String menuItemName) {
        // translate null to NONE as first thing so it can be recognized in mappings (in theory; might not be used/usable or redundant)
        menuItemName = ModelMenuItem.getNoneMenuItemNameAsConstant(menuItemName);
        String res = this.menuItemNameAliasMap.get(menuItemName);
        if (UtilValidate.isNotEmpty(res)) {
            return res;
        } else {
            return menuItemName;
        }
    }

    void addAllSubMenus(Map<String, ModelSubMenu> subMenuMap) {
        for(ModelMenuItem menuItem : getMenuItemList()) {
            menuItem.addAllSubMenus(subMenuMap);
        }
    }

    public String getDefaultMenuItemName() {
        if (model != null && isModelFuncScope()) {
            return model.getDefaultMenuItemName();
        } else {
            return null;
        }
    }

    @Override
    public void accept(ModelWidgetVisitor visitor) throws Exception {
        visitor.visit(this);
    }

    public static class BuildArgs {
        public final GeneralBuildArgs genBuildArgs;
        public final CurrentMenuDefBuildArgs currentMenuDefBuildArgs;
        public String currResource;
        public String forceSubMenuModelScope;

        public List<? extends Element> extraMenuItems;

        /**
         * Specify-all-essentials constructor.
         */
        public BuildArgs(GeneralBuildArgs genBuildArgs, CurrentMenuDefBuildArgs currentMenuDefBuildArgs,
                String currResource, String forceSubMenuModelScope) {
            this.genBuildArgs = genBuildArgs;
            this.currentMenuDefBuildArgs = currentMenuDefBuildArgs;
            this.currResource = currResource;
            this.forceSubMenuModelScope = forceSubMenuModelScope;
            this.extraMenuItems = null;
        }

        /**
         * Preserve-all-essentials constructor.
         */
        public BuildArgs(ModelMenuItem.BuildArgs itemBuildArgs) {
            this.genBuildArgs = itemBuildArgs.genBuildArgs;
            this.currentMenuDefBuildArgs = itemBuildArgs.currentMenuDefBuildArgs;
            this.currResource = itemBuildArgs.currResource;
            this.forceSubMenuModelScope = itemBuildArgs.forceSubMenuModelScope;
            this.extraMenuItems = null;
        }
    }

    @Override
    public String getContainerLocation() {
        // NOTE: this can be slightly indirecting, but it will allow user to find anything required
        return getTopMenu().getFullLocationAndName();
    }

    @Override
    public String getWidgetType() {
        return "sub-menu";
    }

    // SCIPIO: ModelMenuNode methods

    @Override
    public ModelMenuItem getParentNode() {
        return getParentMenuItem();
    }

    @Override
    public List<ModelMenuItem> getChildrenNodes() {
        return menuItemList;
    }

    @Override
    public FlexibleStringExpander getSelected() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public FlexibleStringExpander getDisabled() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public FlexibleStringExpander getExpanded() {
        return expanded;
    }

    public boolean isSeparateMenuTargetStatic(String separateMenuTargetStyle) {
        return this.hasSimpleStyleName(separateMenuTargetStyle);
    }
}
