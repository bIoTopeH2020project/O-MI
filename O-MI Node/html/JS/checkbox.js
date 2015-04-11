/**
 * Class for managing all checkbox instances
 */
function ObjectBoxManager(){
	this.objects = []; // Array for storing objects
	
	/**
	 * Creates a DOM checkbox and adds the reference object to objects array
	 */
	this.addObject = function(id) {
		// Manual HTML code appending using jQuery
		$('<li><div id="drop-' + id + '" class="drop"></div><label><input type="checkbox" class="checkbox" id="' + id + '"/>' +
				id + '</label></li>').appendTo("#objectList"); 
		$('<ul id="list-' + id + '" class="closed-list"></ul>').appendTo("#objectList");
		
		// Add a new ObjectBox to the array
		this.push(new ObjectBox(id, 0));
	}
	
	/**
	 * Pushes an object into the objects array
	 */
	this.push = function(o) {
		this.objects.push(o);
	};
	
	/**
	 * Finds an object from the objects array with the given ID
	 * @param {string} id The id of the object to be found
	 * @returns The object with the given id if found, otherwise returns undefined
	 */
	this.find = function(id) {
		var o;
		this.objects.forEach(function(elem, index, array){
			var temp = elem.find(id);
			
			if(temp){
				o = temp;
				return;
			}
		});
		return o;
	};
}

/**
 * Class for simulating a single checkbox instance
 * @param {string} id The id of the checkbox
 * @param {Number} depth The depth of the checkbox/object in the object tree
 * @param {ObjectBox} parent The parent of the current checkbox/object
 */
function ObjectBox(id, depth, parent){
	this.id = id;
	this.depth = depth;
	this.parent = parent;
	this.children = [];
	
	/**
	 * Gets the path suffix of the data discovery (REST)
	 * @returns The path suffix for the URL data discovery
	 */
	this.getPath = function() {
		if(this.parent){
			return this.parent.getPath() + "/" + this.id;
		}
		return this.id;
	};
	
	/**
	 * Finds a child object with the given id
	 * @param {string} id The ID of the child object to be found
	 * @returns The ObjectBox instance with the given id if found, otherwise returns undefined
	 */
	this.find = function(id) {
		if(this.id === id){
			return this;
		}
		var o;
		this.children.forEach(function(elem, index, array){
			var temp = elem.find(id);
			
			if(temp){
				o = temp;
				return;
			}
		});
		return o;
	};
	
	/**
	 * Creates a DOM checkbox and adds the reference object to children array
	 */
	this.addChild = function(parentId, name, listId) {
		var margin = "20px"; // Custom margin of the list-items
		
		var str = '<li><div id="drop-' + name + '" class="drop"></div><label><input type="checkbox" class="checkbox ' + this.id + '" id="' + name + '"/>' + name + '</label></li>';
		
		$(str).appendTo("#" + listId);
		$("#" + listId).last().css({ marginLeft: margin });
		$('<ul id="list-' + name + '" class="closed-list"></ul>').appendTo("#" + listId);
		$("#" + listId).last().css({ marginLeft: margin });
		$("#" + listId + ":last-child").css({ marginLeft:margin });
		
		this.children.push(new ObjectBox(name, this.depth + 1, this));
	};
	
	/**
	 * Gets the depth of the current object
	 * @returns The depth of the current object
	 */
	this.getDepth = function() {
		return this.depth;
	};
}