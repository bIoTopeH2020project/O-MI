// Generated by CoffeeScript 1.9.3
(function() {
  (function(consts, requests, omi) {
    var cloneAbove, getGroups, readValues, resetInfoItemForm, updateOdf;
    cloneAbove = function() {
      var model, target;
      target = $(this).prev();
      model = target.clone();
      model.find("input").val("");
      model.hide();
      target.after(model);
      return model.slideDown(null, function() {
        return consts.infoitemDialog.modal('handleUpdate');
      });
    };
    consts.afterJquery(function() {
      consts.infoitemDialog = $('#newInfoItem');
      consts.infoitemForm = consts.infoitemDialog.find('form');
      consts.originalInfoItemForm = consts.infoitemForm.clone();
      consts.infoitemDialog.on('hide.bs.modal', function() {
        return resetInfoItemForm();
      });
      resetInfoItemForm();
      consts.infoitemDialog.find('.newInfoSubmit').on('click', function() {
        var infoitemData;
        infoitemData = readValues();
        return updateOdf(infoitemData);
      });
    });
    getGroups = function(ofWhat, requiredField) {
      var arr;
      arr = [];
      consts.infoitemForm.find(ofWhat).each(function() {
        var value;
        value = {};
        $(this).find(":input").each(function() {
          return value[this.name] = $(this).val();
        });
        if ((value[requiredField] != null) && value[requiredField].length > 0) {
          arr.push(value);
        }
        return null;
      });
      return arr;
    };
    readValues = function() {
      var results;
      results = {};
      consts.infoitemForm.find("#infoItemName, #infoItemDescription, #infoItemParent").each(function() {
        return results[this.name] = $(this).val();
      });
      results.values = getGroups(".value-group", "value");
      results.metadatas = getGroups(".metadata-group", "metadataname");
      return results;
    };
    updateOdf = function(newInfoItem) {
      var idName, name, parent, path, tree, v, valueObj, values;
      tree = WebOmi.consts.odfTree;
      parent = newInfoItem.parent;
      name = newInfoItem.name;
      idName = idesc(name);
      path = parent + "/" + idName;
      if ($(jqesc(path)).length > 0) {
        tree.select_node(path);
        $('#infoItemName').tooltip({
          placement: "top",
          title: "InfoItem with this name already exists"
        }).focus().on('input', function() {
          return $(this).tooltip('destroy').closest('.form-group').removeClass('has-error');
        }).closest('.form-group').addClass('has-error');
      } else {
        v = WebOmi.consts.validators;
        values = (function() {
          var i, len, ref, results1;
          ref = newInfoItem.values;
          results1 = [];
          for (i = 0, len = ref.length; i < len; i++) {
            valueObj = ref[i];
            results1.push({
              value: valueObj.value,
              time: v.nonEmpty(valueObj.valuetime),
              type: valueObj.valuetype
            });
          }
          return results1;
        })();
        consts.addOdfTreeNode(parent, path, name, "infoitem", function() {
          return $(jqesc(path)).data("values", newInfoItem.values).data("description", v.nonEmpty(newInfoItem.description));
        });
        if (newInfoItem.metadatas.length > 0) {
          consts.addOdfTreeNode(path, path + "/MetaData", "MetaData", "metadata", function(node) {
            return $(jqesc(node.id)).data("metadatas", newInfoItem.metadatas);
          });
        }
        consts.infoitemDialog.modal('hide');
        resetInfoItemForm();
      }
    };
    return resetInfoItemForm = function() {
      consts.infoitemForm.replaceWith(consts.originalInfoItemForm.clone());
      consts.infoitemForm = $(consts.infoitemDialog.find('form'));
      consts.infoitemForm.submit(function(event) {
        return event.preventDefault();
      });
      consts.infoitemForm.find('.btn-clone-above').on('click', cloneAbove);
    };
  })(WebOmi.consts, WebOmi.requests, WebOmi.omi);

}).call(this);
