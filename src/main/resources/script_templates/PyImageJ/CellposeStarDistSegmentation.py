#@ ImageJ ij

'''
==========================
Building a Python Environment
==========================
NOTE: this script requires a Python environment that includes StarDist and Cellpose.
At time of writing, StarDist  only supports NumPy 1.x, which necessitates using TensorFlow 2.15.
TensorFlow 2.15 itself requires python 3.11 or earlier.

We use cellpose 3.x because cellpose 4.x is heavily biased towards using their `cpsam`,
"segment anything" model. This is a very cool model, but it is also huge and may require GPU use.
`cpsam` is also more powerful than needed for this example.

Using cellpose 3.x allows us to stick with the light and focused `ctyo` model for segmentation.

See the Conda and Pip dependency sections below for specific library versions that were used
to develop this script.

Use these versions to create an appropriate Python environment with:
Edit > Options > Python…

==========================
Platform Dependency Tuning
==========================
NOTE: python can be very sensitive to operating system.
It is possible to install "compatible" versions of libraries that are actually incompatible,
based on the source of the library.
In testing this script, the following guidelines were used:
Windows: numpy should be installed with pip (as indicated below)
MacOS (arm64): numpy should be installed with Conda (NOT with pip)

==========================
Conda-managed dependencies
==========================
python=3.11

==========================
Pip-managed dependencies
==========================
numpy==1.26.4
csbdeep==0.8.0
tensorflow==2.15.1
cellpose==3.1.1.1
stardist==0.9.0

==========================
Future Directions
==========================
The "segment anything" Cellpose model is very powerful. The next steps for this script would be
to remove StarDist and upgrade to Cellpose 4.x, which would be used to segment both cytoplasm and
nuclear channels.

NOTE: Because of the size of the cpsam model used in Cellpose 4.x, they advise against CPU-based segmentation.
Using `gpu=True` with Cellpose models from the Fiji script editor is untested.

==========================
Known Issues
==========================
Although cellpose 3.x "recognizes" the `ctyo` model, all models must be downloaded
once before use (to USER_HOME/.cellpose/models).
Unfortunately the built-in logic for downloading models in cellpose is completely
tied to tqdm, which breaks when running in the script editor on Windows.
Attempts to disable tqdm with environment variables and "monkey patching" failed.
Thus, we provide the `cache_cyto_model` method below to download the model if needed.
'''

from pathlib import Path
import urllib.request

def cache_cyto_model():
    """Ensure the Cellpose 'cyto' model files exist in ~/.cellpose/models/."""
    model_dir = Path.home() / ".cellpose" / "models"
    model_dir.mkdir(parents=True, exist_ok=True)

    files = {
        "cytotorch_0": "https://www.cellpose.org/models/cytotorch_0",
        "size_cytotorch_0.npy": "https://www.cellpose.org/models/size_cytotorch_0.npy"
    }

    for filename, url in files.items():
        target = model_dir / filename
        if not target.exists():
            print(f"Downloading {filename}...")
            urllib.request.urlretrieve(url, target)
            print(f"{filename} downloaded to {target}")
        else:
            print(f"Skipping {filename} - already cached at {target}")

# Download and cache the cyto model
cache_cyto_model()

import imagej.convert as convert
import numpy as np
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
model = models.Cellpose(gpu=False, model_type='cyto')
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

# ROI Conversion Options
# At this point, we have ImgLib2 ROIs. To display in the RoiManager, we need ImageJ 1.x ROIs

# Option 1: convert a single ImgLib2 ROI to a legacy ImageJ ROI with the ConvertService
# This ROI could then be manually added to the Roi manager
imglib_polygon_roi = nuc_roi_tree.children().get(0).data()
ij_polygon_roi = ij.convert().convert(imglib_polygon_roi, sj.jimport('ij.gui.PolygonRoi'))
print(type(ij_polygon_roi))

# Option 2: convert index images to ImageJ ROIs in RoiManager
# This convenience method simplifies the batch conversion and addition to the RoiManager
convert.index_img_to_roi_manager(ij, nuc_labels)
convert.index_img_to_roi_manager(ij, cyto_labels[0])
#TODO any way to color the selections? We can use Colors... but it appears to be global and the last one run wins
#ij.IJ.run(imp, "Colors...", "foreground=blue background=black selection=red");

#TODO this pops an unnecessary display at the end but if I don't make it the last line the ROIs don't show
rm.moveRoisToOverlay(imp)
rm.runCommand(imp, "Show All")
