package indi.etern.musichud.client.ui.render.warp;

/**
 * Forward-warped mesh in normalized local coordinates. Positions may exceed [0, 1];
 * uv is always the undeformed parameter position.
 */
public record WarpMesh(float[] positions, float[] uv, short[] indices,
                       float[] controlPoints, int columns, int rows, int gridSize) {
    public static final int DEFAULT_RESOLUTION = 65;
}
