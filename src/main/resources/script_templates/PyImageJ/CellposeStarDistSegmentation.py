#@ ImageJ ij

'''
Note that this script requires a Python environment that includes StarDist and Cellpose
StarDist currently only supports NumPy 1.x, which necessitates using TensorFlow 2.15 or earlier
TensorFlow 2.15 itself requires python 3.11 or earlier

You can rebuild your Python environment by using:
Edit > Options > Python…

The following configuration was used to develop this script:

--Conda dependencies--
python=3.11
numpy=1.26.4

--Pip dependencies--
tensorflow==2.15
cellpose==4.0.6
stardist==0.9.0
csbdeep==0.8.0
'''

import sys
import imagej.convert as convert
import numpy as np
import matplotlib.pyplot as plt
from cellpose import models
from csbdeep.utils import normalize
from stardist.models import StarDist2D
import scyjava as sj

def filter_index_image(narr:np.ndarray, min_size:int, max_size:int):
    """
    Filter an index image's labels with a pixel size range.
    """
    unique = np.unique(narr)
    for label in unique:
        if label == 0:
            # skip the background
            continue
        
        # create a crop for each label
        bbox = get_bounding_box(np.where(narr == label))
        bbox_crop = narr[bbox[0]:bbox[2] + 1, bbox[1]:bbox[3] + 1].copy()
        bbox_crop[bbox_crop != label] = 0

        # get the number of pixels in label
        bbox_crop = bbox_crop.astype(bool)
        label_size = np.sum(bbox_crop)

        if not min_size <= label_size <= max_size:
            narr[narr == label] = 0
    
    return narr

def get_bounding_box(indices: np.ndarray):
    """
    Get the bounding box coordinates from a the label indices.
    """
    # get min and max bounds of indices array
    min_row = np.min(indices[0])
    min_col = np.min(indices[1])
    max_row = np.max(indices[0])
    max_col = np.max(indices[1])

    return (min_row, min_col, max_row, max_col)

# open image data and convert to Python from Java
#TODO does this connection need to be closed?
data = ij.io().open('https://media.imagej.net/pyimagej/3d/hela_a3g.tif')
xdata = ij.py.from_java(data)

# show the first channel
ij.ui().show("nucleus", ij.py.to_java(xdata[:, :, 0]))

# show the second channel
ij.ui().show("cytoplasm", ij.py.to_java(xdata[:, :, 1] * 125))

# run StarDist on nuclei channel
model = StarDist2D.from_pretrained('2D_versatile_fluo')
nuc_labels, _ = model.predict_instances(normalize(xdata[:, :, 0]))

# run Cellpose on cytoplasm (grayscale)
model = models.CellposeModel(gpu=False, model_type='cyto')
ch = [0, 0]
cyto_labels = model.eval(xdata[:, :, 1].data, channels=ch, diameter=72.1)

# show the stardist results
ij.ui().show("StarDist results", ij.py.to_java(nuc_labels))
ij.IJ.run("mpl-viridis", "");

# show the second channel
ij.ui().show("Cellpose results", ij.py.to_java(cyto_labels[0]))
ij.IJ.run("mpl-viridis", "");

# filter the stardist results and display
filter_index_image(nuc_labels, 500, 10000)
ij.ui().show("StarDist filtered", ij.py.to_java(nuc_labels))
ij.IJ.run("mpl-viridis", "");

# ensure ROI Manager exists
rm = ij.RoiManager.getInstance()
if rm is None:
    ij.IJ.run("ROI Manager...")
    rm = ij.RoiManager.getInstance()

# Reset the ROI manager
rm.reset()

# convert to ImgLib2 ROI in a ROITree
nuc_roi_tree = convert.index_img_to_roi_tree(ij, nuc_labels)
cyto_roi_tree = convert.index_img_to_roi_tree(ij, cyto_labels[0])

# print the contents of the ROITree (nuclear ROIs)
len(nuc_roi_tree.children())
for i in range(len(nuc_roi_tree.children())):
    print(nuc_roi_tree.children().get(i).data())

# display the input data, select channel 2 and enhance the contrast
data_title = "hela_a3g.tif"
ij.ui().show(data_title, data)
imp = ij.WindowManager.getImage(data_title)
imp.setC(2)
ij.IJ.run(imp, "Enhance Contrast", "saturated=0.35")

# convert a single ImgLib2 roi to a legacy ImageJ ROI with the ConvertService.
imglib_polygon_roi = nuc_roi_tree.children().get(0).data()
ij_polygon_roi = ij.convert().convert(imglib_polygon_roi, sj.jimport('ij.gui.PolygonRoi'))
print(type(ij_polygon_roi))

# convert index images to ImageJ ROI in RoiManager
#TODO any way to color the selections? We can use Colors... but it appears to be global and the last one run wins
#ij.IJ.run(imp, "Colors...", "foreground=blue background=black selection=red");
convert.index_img_to_roi_manager(ij, nuc_labels)
convert.index_img_to_roi_manager(ij, cyto_labels[0])

#TODO this pops an unnecessary display at the end but if I don't make it the last line the ROIs don't show
rm.moveRoisToOverlay(imp)
rm.runCommand(imp, "Show All")