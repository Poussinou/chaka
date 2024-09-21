package net.timelegend.chaka.viewer;

import com.artifex.mupdf.fitz.Cookie;
import com.artifex.mupdf.fitz.DisplayList;
import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.Link;
import com.artifex.mupdf.fitz.Matrix;
import com.artifex.mupdf.fitz.Outline;
import com.artifex.mupdf.fitz.Page;
import com.artifex.mupdf.fitz.PDFPage;
import com.artifex.mupdf.fitz.Quad;
import com.artifex.mupdf.fitz.Rect;
import com.artifex.mupdf.fitz.RectI;
import com.artifex.mupdf.fitz.SeekableInputStream;
import com.artifex.mupdf.fitz.android.AndroidDrawDevice;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;

import java.util.ArrayList;

public class MuPDFCore
{
	private int resolution;
	private Document doc;
	private Outline[] outline;
	private int pageCount = -1;
	private boolean reflowable = false;
	private int currentPage;
	private Page page;
	private float pageWidth;
	private float pageHeight;
	private DisplayList displayList;
    private boolean singleColumnMode = false;
    private boolean textLeftMode = false;
    private boolean cropMarginMode = false;

	/* Default to "A Format" pocket book size. */
	private int layoutW = 312;
	private int layoutH = 504;
	private int layoutEM = 10;

	private MuPDFCore(Document doc) {
		this.doc = doc;
		doc.layout(layoutW, layoutH, layoutEM);
        correctPageCount();
		reflowable = doc.isReflowable();
		resolution = 160;
		currentPage = -1;
	}

	public MuPDFCore(byte buffer[], String magic) {
		this(Document.openDocument(buffer, magic));
	}

	public MuPDFCore(SeekableInputStream stm, String magic) {
		this(Document.openDocument(stm, magic));
	}

	public String getTitle() {
		return doc.getMetaData(Document.META_INFO_TITLE);
	}

	public int countPages() {
		return pageCount;
	}

	public boolean isReflowable() {
		return reflowable;
	}

	public synchronized int layout(int oldPage, int w, int h, int em) {
		if (w != layoutW || h != layoutH || em != layoutEM) {
            oldPage = realPage(oldPage);
			System.out.println("LAYOUT: " + w + "," + h);
			layoutW = w;
			layoutH = h;
			layoutEM = em;
			long mark = doc.makeBookmark(doc.locationFromPageNumber(oldPage));
			doc.layout(layoutW, layoutH, layoutEM);
			currentPage = -1;
            correctPageCount();
			outline = null;
			try {
				outline = doc.loadOutline();
			} catch (Exception ex) {
				/* ignore error */
			}
			return correctPage(doc.pageNumberFromLocation(doc.findBookmark(mark)));
		}
		return oldPage;
	}

    // the pageNum is correctPage
	private synchronized void gotoPage(int pageNum) {
		/* TODO: page cache */
		if (pageNum > pageCount-1)
			pageNum = pageCount-1;
		else if (pageNum < 0)
			pageNum = 0;
		if (pageNum != currentPage) {
			if (page != null)
				page.destroy();
			page = null;
			if (displayList != null)
				displayList.destroy();
			displayList = null;
			page = null;
			pageWidth = 0;
			pageHeight = 0;
			currentPage = pageNum;

			if (doc != null) {
                pageNum = realPage(pageNum);
				page = doc.loadPage(pageNum);
                Rect b = page.getBounds();

                if (cropMarginMode) {
                    b = getBBox(b);
                }

				pageWidth = b.x1 - b.x0;
				pageHeight = b.y1 - b.y0;
			}
		}
	}

    /*
     * page full size
     */
	public synchronized PointF getPageSize(int pageNum) {
		gotoPage(pageNum);
		return new PointF(pageWidth, pageHeight);
	}

	public synchronized void onDestroy() {
		if (displayList != null)
			displayList.destroy();
		displayList = null;
		if (page != null)
			page.destroy();
		page = null;
		if (doc != null)
			doc.destroy();
		doc = null;
	}

	public synchronized void drawPage(Bitmap bm, int pageNum,
			int pageW, int pageH,
			int patchX, int patchY,
			int patchW, int patchH,
			Cookie cookie) {
		gotoPage(pageNum);

		if (displayList == null && page != null)
			try {
				displayList = page.toDisplayList();
			} catch (Exception ex) {
				displayList = null;
			}

		if (displayList == null || page == null)
			return;

        if (isSplitPage(currentPage)) {
            pageW *= 2;
        }

		float zoom = resolution / 72;
		Matrix ctm = new Matrix(zoom, zoom);
        Rect b = page.getBounds();

        if (cropMarginMode) {
            b = getBBox(b);
        }

		RectI bbox = new RectI(b.transform(ctm));
		float xscale = (float)pageW / (float)(bbox.x1-bbox.x0);
		float yscale = (float)pageH / (float)(bbox.y1-bbox.y0);
		ctm.scale(xscale, yscale);

        if (cropMarginMode) {
            patchX += bbox.x0 * xscale;
            patchY += bbox.y0 * yscale;
        }

		AndroidDrawDevice dev = new AndroidDrawDevice(bm, patchX, patchY);
		try {
			displayList.run(dev, ctm, cookie);
			dev.close();
		} finally {
			dev.destroy();
		}
	}

	public synchronized void updatePage(Bitmap bm, int pageNum,
			int pageW, int pageH,
			int patchX, int patchY,
			int patchW, int patchH,
			Cookie cookie) {
		drawPage(bm, pageNum, pageW, pageH, patchX, patchY, patchW, patchH, cookie);
	}

    public void toggleSingleColumn() {
        singleColumnMode = !singleColumnMode;
        correctPageCount();
    }

    public void toggleTextLeft() {
        textLeftMode = !textLeftMode;
    }

    public void toggleCropMargin() {
        // force reread pagesize of current page
        // prevent display distort when uncrop margin after a page scale
        currentPage = -1;
        cropMarginMode = !cropMarginMode;
    }

	public synchronized Link[] getPageLinks(int pageNum) {
		gotoPage(pageNum);
		return page != null ? page.getLinks() : null;
	}

	public synchronized int resolveLink(Link link) {
        return correctPage(doc.pageNumberFromLocation(doc.resolveLink(link)));
	}

	public synchronized Quad[][] searchPage(int pageNum, String text) {
		gotoPage(pageNum);
		Quad[][] ret = page.search(text);
        if (!isSplitPage(pageNum))
            return ret;

        ArrayList<Quad[]> reslist = new ArrayList<>();
        float mid = pageWidth / 2;
        for (Quad[] r : ret) {
            for (Quad q : r) {
                if ( (!isRightPage(pageNum) && q.ul_x < mid) || (isRightPage(pageNum) && q.ur_x > mid) ) {
                    reslist.add(r);
                    break;
                }
            }
        }
        if (reslist.size() > 0) {
            Quad[][] res = new Quad[reslist.size()][];
            res = reslist.toArray(res);
            return res;
        }
        return null;
	}

	public synchronized boolean hasOutline() {
		if (outline == null) {
			try {
				outline = doc.loadOutline();
			} catch (Exception ex) {
				/* ignore error */
			}
		}
		return outline != null;
	}

	private void flattenOutlineNodes(ArrayList<OutlineActivity.Item> result, Outline list[], int level) {
		for (Outline node : list) {
			if (node.title != null) {
				int pageNum = correctPage(doc.pageNumberFromLocation(doc.resolveLink(node)));
                int count = 0;
                if (node.down != null) {
                    count = node.down.length;
                }
				result.add(new OutlineActivity.Item(node.title, pageNum, level, count));
			    if (count > 0)
				    flattenOutlineNodes(result, node.down, level + 1);
			}
		}
	}

	public synchronized ArrayList<OutlineActivity.Item> getOutline() {
		ArrayList<OutlineActivity.Item> result = new ArrayList<OutlineActivity.Item>();
		flattenOutlineNodes(result, outline, 0);
		return result;
	}

    private Rect getBBox(Rect b) {
        Rect r = page.getBBox();
        r.inset(-2, -2, -2, -2);

        // an option: let r similar to b
        // that results in less better effect but consistent display
        //
        // float rw = r.x1 - r.x0;
        // float rh = r.y1 - r.y0;
        // float sr = rh / rw;
        // float sb = (b.y1 - b.y0) / (b.x1 - b.x0);
        // float delta;
        // if (sr < sb) {
        //     float rh2 = rw * sb;
        //     delta = (rh2 - rh) / 2;
        //     r.y0 -= delta;
        //     r.y1 += delta;
        // }
        // else if (sr > sb) {
        //     float rw2 = rh / sb;
        //     delta = (rw2 - rw) / 2;
        //     r.x0 -= delta;
        //     r.x1 += delta;
        // }
        //

        return r;
    }

    public boolean isSingleColumn() {
        return singleColumnMode;
    }

    public boolean isSplitPage(int pageNum) {
        return singleColumnMode && pageNum > 0 && pageNum < (pageCount - 1);
    }

    /*
     * for splitted page
     */
    public boolean isRightPage(int pageNum) {
        return (textLeftMode && pageNum % 2 == 1) || (!textLeftMode && pageNum % 2 == 0);
    }

    public boolean isTextLeft() {
        return textLeftMode;
    }

    private void correctPageCount() {
        if (singleColumnMode)
            // divide every page into 2 pages, except first and last page
		    pageCount = doc.countPages() * 2 - 2;
        else
		    pageCount = doc.countPages();
    }

    public int correctPage(int p) {
        if (singleColumnMode) {
            p = (p * 2) - 1;
            if (p < 0) p = 0;
        }
        return p;
    }

    public int realPage(int p) {
        if (singleColumnMode)
            return (p + 1) / 2;
        return p;
    }

	public synchronized boolean needsPassword() {
		return doc.needsPassword();
	}

	public synchronized boolean authenticatePassword(String password) {
		return doc.authenticatePassword(password);
	}
}
