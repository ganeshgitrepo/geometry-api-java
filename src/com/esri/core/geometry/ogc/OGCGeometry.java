package com.esri.core.geometry.ogc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.json.JSONException;

import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.Envelope1D;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.GeometryCursor;
import com.esri.core.geometry.GeometryCursorAppend;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.MapGeometry;
import com.esri.core.geometry.MapOGCStructure;
import com.esri.core.geometry.MultiPoint;
import com.esri.core.geometry.OGCStructure;
import com.esri.core.geometry.Operator;
import com.esri.core.geometry.OperatorBuffer;
import com.esri.core.geometry.OperatorConvexHull;
import com.esri.core.geometry.OperatorExportToWkb;
import com.esri.core.geometry.OperatorFactoryLocal;
import com.esri.core.geometry.OperatorImportFromESRIShape;
import com.esri.core.geometry.OperatorImportFromGeoJson;
import com.esri.core.geometry.OperatorImportFromJson;
import com.esri.core.geometry.OperatorImportFromWkb;
import com.esri.core.geometry.OperatorImportFromWkt;
import com.esri.core.geometry.OperatorIntersection;
import com.esri.core.geometry.OperatorSimplify;
import com.esri.core.geometry.OperatorSimplifyOGC;
import com.esri.core.geometry.OperatorUnion;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polygon;
import com.esri.core.geometry.Polyline;
import com.esri.core.geometry.SimpleGeometryCursor;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.geometry.VertexDescription;

/**
 * OGC Simple Feature Access specification v.1.2.1
 * 
 */
public abstract class OGCGeometry {
	public int dimension() {
		return getEsriGeometry().getDimension();
	}

	public int coordinateDimension() {
		int d = 2;
		if (getEsriGeometry().getDescription().hasAttribute(
				VertexDescription.Semantics.M))
			d++;
		if (getEsriGeometry().getDescription().hasAttribute(
				VertexDescription.Semantics.Z))
			d++;

		return d;
	}

	abstract public String geometryType();

	public int SRID() {
		if (esriSR == null)
			return 0;

		return esriSR.getID();
	}

	public OGCGeometry envelope() {
		com.esri.core.geometry.Envelope env = new com.esri.core.geometry.Envelope();
		getEsriGeometry().queryEnvelope(env);
		com.esri.core.geometry.Polygon polygon = new com.esri.core.geometry.Polygon();
		polygon.addEnvelope(env, false);
		return new OGCPolygon(polygon, esriSR);
	}

	public String asText() {
		return GeometryEngine.geometryToWkt(getEsriGeometry(), 0);
	}

	public ByteBuffer asBinary() {
		OperatorExportToWkb op = (OperatorExportToWkb) OperatorFactoryLocal
				.getInstance().getOperator(Operator.Type.ExportToWkb);
		return op.execute(0, getEsriGeometry(), null);
	}

	public boolean isEmpty() {
		return getEsriGeometry().isEmpty();
	}

	public double MinZ() {
		Envelope1D e = getEsriGeometry().queryInterval(
				VertexDescription.Semantics.Z, 0);
		return e.vmin;
	}

	public double MaxZ() {
		Envelope1D e = getEsriGeometry().queryInterval(
				VertexDescription.Semantics.Z, 0);
		return e.vmax;
	}

	public double MinMeasure() {
		Envelope1D e = getEsriGeometry().queryInterval(
				VertexDescription.Semantics.M, 0);
		return e.vmin;
	}

	public double MaxMeasure() {
		Envelope1D e = getEsriGeometry().queryInterval(
				VertexDescription.Semantics.M, 0);
		return e.vmax;
	}

	public boolean isSimple() {
		return OperatorSimplifyOGC.local().isSimpleOGC(getEsriGeometry(),
				esriSR, true, null, null);
	}

	/**
	 * Extension method - checks if geometry is simple for Geodatabase.
	 * 
	 * @return Returns true if geometry is simple, false otherwise.
	 */
	public boolean isSimpleRelaxed() {
		OperatorSimplify op = (OperatorSimplify) OperatorFactoryLocal
				.getInstance().getOperator(Operator.Type.Simplify);
		return op
				.isSimpleAsFeature(getEsriGeometry(), esriSR, true, null, null);
	}

	/**
	 * Makes a simple geometry for Geodatabase.
	 * 
	 * @return Returns simplified geometry.
	 */
	public OGCGeometry MakeSimpleRelaxed(boolean forceProcessing) {
		OperatorSimplify op = (OperatorSimplify) OperatorFactoryLocal
				.getInstance().getOperator(Operator.Type.Simplify);
		return OGCGeometry.createFromEsriGeometry(
				op.execute(getEsriGeometry(), esriSR, forceProcessing, null),
				esriSR);
	}

	public boolean is3D() {
		return getEsriGeometry().getDescription().hasAttribute(
				VertexDescription.Semantics.Z);
	}

	public boolean isMeasured() {
		return getEsriGeometry().getDescription().hasAttribute(
				VertexDescription.Semantics.M);
	}

	abstract public OGCGeometry boundary();

	// query
	public boolean equals(OGCGeometry another) {
		com.esri.core.geometry.Geometry geom1 = getEsriGeometry();
		com.esri.core.geometry.Geometry geom2 = another.getEsriGeometry();
		return com.esri.core.geometry.GeometryEngine.equals(geom1, geom2,
				getEsriSpatialReference());
	}

	public boolean disjoint(OGCGeometry another) {
		com.esri.core.geometry.Geometry geom1 = getEsriGeometry();
		com.esri.core.geometry.Geometry geom2 = another.getEsriGeometry();
		return com.esri.core.geometry.GeometryEngine.disjoint(geom1, geom2,
				getEsriSpatialReference());
	}

	public boolean intersects(OGCGeometry another) {
		return !disjoint(another);
	}

	public boolean touches(OGCGeometry another) {
		com.esri.core.geometry.Geometry geom1 = getEsriGeometry();
		com.esri.core.geometry.Geometry geom2 = another.getEsriGeometry();
		return com.esri.core.geometry.GeometryEngine.touches(geom1, geom2,
				getEsriSpatialReference());
	}

	public boolean crosses(OGCGeometry another) {
		com.esri.core.geometry.Geometry geom1 = getEsriGeometry();
		com.esri.core.geometry.Geometry geom2 = another.getEsriGeometry();
		return com.esri.core.geometry.GeometryEngine.crosses(geom1, geom2,
				getEsriSpatialReference());
	}

	public boolean within(OGCGeometry another) {
		com.esri.core.geometry.Geometry geom1 = getEsriGeometry();
		com.esri.core.geometry.Geometry geom2 = another.getEsriGeometry();
		return com.esri.core.geometry.GeometryEngine.within(geom1, geom2,
				getEsriSpatialReference());
	}

	public boolean contains(OGCGeometry another) {
		com.esri.core.geometry.Geometry geom1 = getEsriGeometry();
		com.esri.core.geometry.Geometry geom2 = another.getEsriGeometry();
		return com.esri.core.geometry.GeometryEngine.contains(geom1, geom2,
				getEsriSpatialReference());
	}

	public boolean overlaps(OGCGeometry another) {
		com.esri.core.geometry.Geometry geom1 = getEsriGeometry();
		com.esri.core.geometry.Geometry geom2 = another.getEsriGeometry();
		return com.esri.core.geometry.GeometryEngine.overlaps(geom1, geom2,
				getEsriSpatialReference());
	}

	public boolean relate(OGCGeometry another, String matrix) {
		com.esri.core.geometry.Geometry geom1 = getEsriGeometry();
		com.esri.core.geometry.Geometry geom2 = another.getEsriGeometry();
		return com.esri.core.geometry.GeometryEngine.relate(geom1, geom2,
				getEsriSpatialReference(), matrix);
	}

	abstract public OGCGeometry locateAlong(double mValue);

	abstract public OGCGeometry locateBetween(double mStart, double mEnd);

	// analysis
	public double distance(OGCGeometry another) {
		com.esri.core.geometry.Geometry geom1 = getEsriGeometry();
		com.esri.core.geometry.Geometry geom2 = another.getEsriGeometry();
		return com.esri.core.geometry.GeometryEngine.distance(geom1, geom2,
				getEsriSpatialReference());
	}

	// This method firstly groups geometries by dimension (points, lines,
	// areas),
	// then simplifies each group such that each group is reduced to a single
	// geometry.
	// As a result there are at most three geometries, each geometry is Simple.
	// Afterwards
	// it produces a single OGCGeometry.
	//
	// Note: Not complete yet. We'll use this method to implement the OGC
	// Simplify (or MakeValid method)
	// At this moment, method removes self intersections, and clusters vertices,
	// but may sometimes
	// produce geometries with self-tangency or polygons with disconnected
	// interior
	// which are simple for ArcObjects, but non-simple for OGC.
	private OGCGeometry simplifyBunch_(GeometryCursor gc) {
		// Combines geometries into multipoint, polyline, and polygon types,
		// simplifying them and unioning them,
		// then produces OGCGeometry from the result.
		// Can produce OGCConcreteGoemetryCollection
		MultiPoint dstMultiPoint = null;
		ArrayList<Geometry> dstPolylines = new ArrayList<Geometry>();
		ArrayList<Geometry> dstPolygons = new ArrayList<Geometry>();
		for (com.esri.core.geometry.Geometry g = gc.next(); g != null; g = gc
				.next()) {
			switch (g.getType()) {
			case Point:
				if (dstMultiPoint == null)
					dstMultiPoint = new MultiPoint();
				dstMultiPoint.add((Point) g);
				break;
			case MultiPoint:
				if (dstMultiPoint == null)
					dstMultiPoint = new MultiPoint();
				dstMultiPoint.add((MultiPoint) g, 0, -1);
				break;
			case Polyline:
				dstPolylines.add((Polyline) g.copy());
				break;
			case Polygon:
				dstPolygons.add((Polygon) g.copy());
				break;
			default:
				throw new UnsupportedOperationException();
			}
		}

		ArrayList<Geometry> result = new ArrayList<Geometry>(3);
		if (dstMultiPoint != null) {
			Geometry resMP = OperatorSimplifyOGC.local().execute(dstMultiPoint,
					esriSR, true, null);
			result.add(resMP);
		}

		if (dstPolylines.size() > 0) {
			if (dstPolylines.size() == 1) {
				Geometry resMP = OperatorSimplifyOGC.local().execute(
						dstPolylines.get(0), esriSR, true, null);
				result.add(resMP);
			} else {
				GeometryCursor res = OperatorUnion.local().execute(
						new SimpleGeometryCursor(dstPolylines), esriSR, null);
				Geometry resPolyline = res.next();
				Geometry resMP = OperatorSimplifyOGC.local().execute(
						resPolyline, esriSR, true, null);
				result.add(resMP);
			}
		}

		if (dstPolygons.size() > 0) {
			if (dstPolygons.size() == 1) {
				Geometry resMP = OperatorSimplifyOGC.local().execute(
						dstPolygons.get(0), esriSR, true, null);
				result.add(resMP);
			} else {
				GeometryCursor res = OperatorUnion.local().execute(
						new SimpleGeometryCursor(dstPolygons), esriSR, null);
				Geometry resPolygon = res.next();
				Geometry resMP = OperatorSimplifyOGC.local().execute(
						resPolygon, esriSR, true, null);
				result.add(resMP);
			}
		}

		return OGCGeometry.createFromEsriCursor(
				new SimpleGeometryCursor(result), esriSR);
	}

	public OGCGeometry buffer(double distance) {
		OperatorBuffer op = (OperatorBuffer) OperatorFactoryLocal.getInstance()
				.getOperator(Operator.Type.Buffer);
		if (distance == 0) {// when distance is 0, return self (maybe we should
							// create a copy instead).
			return this;
		}

		double d[] = { distance };
		com.esri.core.geometry.GeometryCursor cursor = op.execute(
				getEsriGeometryCursor(), getEsriSpatialReference(), d, true,
				null);
		return OGCGeometry.createFromEsriGeometry(cursor.next(), esriSR);
	}

	public OGCGeometry convexHull() {
		com.esri.core.geometry.OperatorConvexHull op = (OperatorConvexHull) OperatorFactoryLocal
				.getInstance().getOperator(Operator.Type.ConvexHull);
		com.esri.core.geometry.GeometryCursor cursor = op.execute(
				getEsriGeometryCursor(), true, null);
		return OGCGeometry.createFromEsriCursor(cursor, esriSR);
	}

	public OGCGeometry intersection(OGCGeometry another) {
		com.esri.core.geometry.OperatorIntersection op = (OperatorIntersection) OperatorFactoryLocal
				.getInstance().getOperator(Operator.Type.Intersection);
		com.esri.core.geometry.GeometryCursor cursor = op.execute(
				getEsriGeometryCursor(), another.getEsriGeometryCursor(),
				getEsriSpatialReference(), null, 7);
		return OGCGeometry.createFromEsriCursor(cursor, esriSR, true);
	}

	public OGCGeometry union(OGCGeometry another) {
		OperatorUnion op = (OperatorUnion) OperatorFactoryLocal.getInstance()
				.getOperator(Operator.Type.Union);
		GeometryCursorAppend ap = new GeometryCursorAppend(
				getEsriGeometryCursor(), another.getEsriGeometryCursor());
		com.esri.core.geometry.GeometryCursor cursor = op.execute(ap,
				getEsriSpatialReference(), null);
		return OGCGeometry.createFromEsriCursor(cursor, esriSR);
	}

	public OGCGeometry difference(OGCGeometry another) {
		com.esri.core.geometry.Geometry geom1 = getEsriGeometry();
		com.esri.core.geometry.Geometry geom2 = another.getEsriGeometry();
		return createFromEsriGeometry(
				com.esri.core.geometry.GeometryEngine.difference(geom1, geom2,
						getEsriSpatialReference()), esriSR);
	}

	public OGCGeometry symDifference(OGCGeometry another) {
		com.esri.core.geometry.Geometry geom1 = getEsriGeometry();
		com.esri.core.geometry.Geometry geom2 = another.getEsriGeometry();
		return createFromEsriGeometry(
				com.esri.core.geometry.GeometryEngine.symmetricDifference(
						geom1, geom2, getEsriSpatialReference()), esriSR);
	}

	public abstract com.esri.core.geometry.Geometry getEsriGeometry();

	public GeometryCursor getEsriGeometryCursor() {
		return new SimpleGeometryCursor(getEsriGeometry());
	}

	public com.esri.core.geometry.SpatialReference getEsriSpatialReference() {
		return esriSR;
	}

	/**
	 * Create an OGCGeometry instance from the GeometryCursor.
	 * 
	 * @param gc
	 * @param sr
	 * @return Geometry instance created from the geometry cursor.
	 */
	public static OGCGeometry createFromEsriCursor(GeometryCursor gc,
			SpatialReference sr) {
		return createFromEsriCursor(gc, sr, false);
	}

	public static OGCGeometry createFromEsriCursor(GeometryCursor gc,
			SpatialReference sr, boolean skipEmpty) {
		ArrayList<OGCGeometry> geoms = new ArrayList<OGCGeometry>(10);
		Geometry emptyGeom = null;
		for (Geometry g = gc.next(); g != null; g = gc.next()) {
			emptyGeom = g;
			if (!skipEmpty || !g.isEmpty())
				geoms.add(createFromEsriGeometry(g, sr));
		}

		if (geoms.size() == 1) {
			return geoms.get(0);
		} else if (geoms.size() == 0)
			return createFromEsriGeometry(emptyGeom, sr);
		else
			return new OGCConcreteGeometryCollection(geoms, sr);
	}

	public static OGCGeometry fromText(String text) {
		OperatorImportFromWkt op = (OperatorImportFromWkt) OperatorFactoryLocal
				.getInstance().getOperator(Operator.Type.ImportFromWkt);
		OGCStructure ogcStructure = op.executeOGC(0, text, null);
		return OGCGeometry.createFromOGCStructure(ogcStructure,
				SpatialReference.create(4326));
	}

	public static OGCGeometry fromBinary(ByteBuffer binary) {
		OperatorImportFromWkb op = (OperatorImportFromWkb) OperatorFactoryLocal
				.getInstance().getOperator(Operator.Type.ImportFromWkb);
		OGCStructure ogcStructure = op.executeOGC(0, binary, null);
		return OGCGeometry.createFromOGCStructure(ogcStructure,
				SpatialReference.create(4326));
	}

	public static OGCGeometry fromEsriShape(ByteBuffer buffer) {
		OperatorImportFromESRIShape op = (OperatorImportFromESRIShape) OperatorFactoryLocal
				.getInstance().getOperator(Operator.Type.ImportFromESRIShape);
		Geometry g = op.execute(0, Geometry.Type.Unknown, buffer);
		return OGCGeometry.createFromEsriGeometry(g,
				SpatialReference.create(4326));
	}

	public static OGCGeometry fromJson(String string)
			throws JsonParseException, IOException {
		JsonFactory factory = new JsonFactory();
		JsonParser jsonParserPt = factory.createJsonParser(string);
		jsonParserPt.nextToken();
		MapGeometry mapGeom = GeometryEngine.jsonToGeometry(jsonParserPt);
		return OGCGeometry.createFromEsriGeometry(mapGeom.getGeometry(),
				mapGeom.getSpatialReference());
	}

	public static OGCGeometry fromGeoJson(String string) throws JSONException {
		OperatorImportFromGeoJson op = (OperatorImportFromGeoJson) OperatorFactoryLocal
				.getInstance().getOperator(Operator.Type.ImportFromGeoJson);
		MapOGCStructure mapOGCStructure = op.executeOGC(0, string, null);
		return OGCGeometry.createFromOGCStructure(
				mapOGCStructure.m_ogcStructure,
				mapOGCStructure.m_spatialReference);
	}

	public static OGCGeometry createFromEsriGeometry(Geometry geom,
			SpatialReference sr) {
		return createFromEsriGeometry(geom, sr, false);
	}

	public static OGCGeometry createFromEsriGeometry(Geometry geom,
			SpatialReference sr, boolean multiType) {
		if (geom == null)
			return null;
		Geometry.Type t = geom.getType();
		if (t == Geometry.Type.Polygon) {
			if (!multiType && ((Polygon) geom).getExteriorRingCount() == 1)
				return new OGCPolygon((Polygon) geom, sr);
			else
				return new OGCMultiPolygon((Polygon) geom, sr);
		}
		if (t == Geometry.Type.Polyline) {
			if (!multiType && ((Polyline) geom).getPathCount() == 1)
				return new OGCLineString((Polyline) geom, 0, sr);
			else
				return new OGCMultiLineString((Polyline) geom, sr);
		}
		if (t == Geometry.Type.MultiPoint) {
			if (!multiType && ((MultiPoint) geom).getPointCount() <= 1) {
				if (geom.isEmpty())
					return new OGCPoint(new Point(), sr);
				else
					return new OGCPoint(((MultiPoint) geom).getPoint(0), sr);
			} else
				return new OGCMultiPoint((MultiPoint) geom, sr);
		}
		if (t == Geometry.Type.Point) {
			if (!multiType) {
				return new OGCPoint((Point) geom, sr);
			} else {
				return new OGCMultiPoint((Point) geom, sr);
			}
		}
		if (t == Geometry.Type.Envelope) {
			Polygon p = new Polygon();
			p.addEnvelope((Envelope) geom, false);
			return createFromEsriGeometry(p, sr, multiType);
		}

		throw new UnsupportedOperationException();
	}

	public static OGCGeometry createFromOGCStructure(OGCStructure ogcStructure,
			SpatialReference sr) {
		ArrayList<OGCConcreteGeometryCollection> collectionStack = new ArrayList<OGCConcreteGeometryCollection>(
				0);
		ArrayList<OGCStructure> structureStack = new ArrayList<OGCStructure>(0);
		ArrayList<Integer> indices = new ArrayList(0);

		OGCGeometry[] geometries = new OGCGeometry[1];
		OGCConcreteGeometryCollection root = new OGCConcreteGeometryCollection(
				Arrays.asList(geometries), sr);

		structureStack.add(ogcStructure);
		collectionStack.add(root);
		indices.add(0);

		while (!structureStack.isEmpty()) {
			OGCStructure lastStructure = structureStack.get(structureStack
					.size() - 1);
			if (indices.get(indices.size() - 1) == lastStructure.m_structures
					.size()) {
				structureStack.remove(structureStack.size() - 1);
				collectionStack.remove(collectionStack.size() - 1);
				indices.remove(indices.size() - 1);
				continue;
			}

			OGCConcreteGeometryCollection lastCollection = collectionStack
					.get(collectionStack.size() - 1);
			OGCGeometry g;
			int i = indices.get(indices.size() - 1);

			int type = lastStructure.m_structures.get(i).m_type;

			switch (type) {
			case 1:
				g = new OGCPoint(
						(Point) lastStructure.m_structures.get(i).m_geometry,
						sr);
				lastCollection.geometries.set(i, g);
				indices.set(indices.size() - 1, i + 1);
				break;
			case 2:
				g = new OGCLineString(
						(Polyline) lastStructure.m_structures.get(i).m_geometry,
						0, sr);
				lastCollection.geometries.set(i, g);
				indices.set(indices.size() - 1, i + 1);
				break;
			case 3:
				g = new OGCPolygon(
						(Polygon) lastStructure.m_structures.get(i).m_geometry,
						0, sr);
				lastCollection.geometries.set(i, g);
				indices.set(indices.size() - 1, i + 1);
				break;
			case 4:
				g = new OGCMultiPoint(
						(MultiPoint) lastStructure.m_structures.get(i).m_geometry,
						sr);
				lastCollection.geometries.set(i, g);
				indices.set(indices.size() - 1, i + 1);
				break;
			case 5:
				g = new OGCMultiLineString(
						(Polyline) lastStructure.m_structures.get(i).m_geometry,
						sr);
				lastCollection.geometries.set(i, g);
				indices.set(indices.size() - 1, i + 1);
				break;
			case 6:
				g = new OGCMultiPolygon(
						(Polygon) lastStructure.m_structures.get(i).m_geometry,
						sr);
				lastCollection.geometries.set(i, g);
				indices.set(indices.size() - 1, i + 1);
				break;
			case 7:
				geometries = new OGCGeometry[lastStructure.m_structures.get(i).m_structures
						.size()];
				g = new OGCConcreteGeometryCollection(
						Arrays.asList(geometries), sr);
				lastCollection.geometries.set(i, g);
				indices.set(indices.size() - 1, i + 1);
				structureStack.add(lastStructure.m_structures.get(i));
				collectionStack.add((OGCConcreteGeometryCollection) g);
				indices.add(0);
				break;
			default:
				throw new UnsupportedOperationException();
			}
		}

		return root.geometries.get(0);
	}

	protected boolean isConcreteGeometryCollection() {
		return false;
	}

	/**
	 * SpatialReference of the Geometry.
	 */
	public com.esri.core.geometry.SpatialReference esriSR;

	public void setSpatialReference(SpatialReference esriSR_) {
		esriSR = esriSR_;
	}
}
