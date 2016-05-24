/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.projecttango.examples.java.planefitting;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoPoseData;

import android.content.Context;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;

import org.json.JSONArray;
import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.renderer.RajawaliRenderer;

import javax.microedition.khronos.opengles.GL10;

import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;

import com.loopj.android.http.*;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

/**
 * Very simple example augmented reality renderer which displays a cube fixed in place.
 * The position of the cube in the OpenGL world is updated using the {@code updateObjectPose}
 * method.
 */
public class PlaneFittingRenderer extends RajawaliRenderer {
    private static final float CUBE_SIDE_LENGTH = 0.5f;
    private static final int MAX_ITEMS = 30;
    private static final String TAG = PlaneFittingRenderer.class.getSimpleName();

    // Augmented Reality related fields
    private ATexture mTangoCameraTexture;
    private boolean mSceneCameraConfigured;

    private ArrayList<Object3D> mObjects;
    private ArrayList<Vector3> mObjectPoses;
    private boolean mObjectPoseUpdated = false;

    private static final String ENDPOINT = "http://10.34.180.16:3000/notes";

    public PlaneFittingRenderer(Context context) {
        super(context);
    }

    @Override
    protected void initScene() {
        // Create a quad covering the whole background and assign a texture to it where the
        // Tango color camera contents will be rendered.
        ScreenQuad backgroundQuad = new ScreenQuad();
        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0);
        // We need to use Rajawali's {@code StreamingTexture} since it sets up the texture
        // for GL_TEXTURE_EXTERNAL_OES rendering
        mTangoCameraTexture =
                new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
        try {
            tangoCameraMaterial.addTexture(mTangoCameraTexture);
            backgroundQuad.setMaterial(tangoCameraMaterial);
        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception creating texture for RGB camera contents", e);
        }
        getCurrentScene().addChildAt(backgroundQuad, 0);

        // Add a directional light in an arbitrary direction.
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);

        mObjects = new ArrayList<Object3D>();
        mObjectPoses = new ArrayList<Vector3>();

        // Magic - For some reason, we need to add all the items
        for (int i = 0; i < MAX_ITEMS; i++) {
            addStartingObjects();
        }
    }

    private void addStartingObjects() {
        Material material = new Material();
        material.setColor(0xff009900);
        try {
            material.addTexture(new Texture("instructions", R.drawable.clear));
        } catch (ATexture.TextureException e) {
            e.printStackTrace();
        }
        material.setColorInfluence(0.1f);
        material.enableLighting(true);
        material.setDiffuseMethod(new DiffuseMethod.Lambert());

        // Build a Cube and place it initially in the origin.
        Object3D newObject = new Cube(0.1f);
        newObject.setMaterial(material);
        newObject.setPosition(0, 0, -3);
        newObject.setRotation(Vector3.Axis.Y, 180);
        getCurrentScene().addChild(newObject);
    }

    private void addObject(Bitmap bmp) {

        Texture t = new Texture("fangalicious", bmp);
        // Set-up a material: green with application of the light and
        // instructions.
        Material material = new Material();
        material.setColor(0xff009900);
        try {
            material.addTexture(t);
        } catch (ATexture.TextureException e) {
            e.printStackTrace();
        }
        material.setColorInfluence(0.1f);
        material.enableLighting(true);
        material.setDiffuseMethod(new DiffuseMethod.Lambert());

        // Build a Cube and place it initially in the origin.
        Object3D newObject = new Cube(CUBE_SIDE_LENGTH);
        newObject.setMaterial(material);
        newObject.setPosition(0, 0, -3);
        newObject.setRotation(Vector3.Axis.Y, 180);
        this.mObjects.add(newObject);
        getCurrentScene().addChild(newObject);
    }

    @Override
    protected void onRender(long elapsedRealTime, double deltaTime) {
        // Update the AR object if necessary
        // Synchronize against concurrent access with the setter below.
        synchronized (this) {

            if (mObjectPoseUpdated && mObjects.size() > 0) {
                for (int i = 0; i < mObjects.size(); i++) {
                    Log.d("ANDREW", "rendering");
                    mObjects.get(i).setPosition(mObjectPoses.get(i));
                }

                mObjectPoseUpdated = false;

            }
        }

        super.onRender(elapsedRealTime, deltaTime);
    }

    /**
     * Save the updated plane fit pose to update the AR object on the next render pass.
     * This is synchronized against concurrent access in the render loop above.
     */
    public synchronized void updateObjectPose(TangoPoseData planeFitPose, Bitmap bmp) {

        Pose p = ScenePoseCalculator.toOpenGLPose(planeFitPose);
        mObjectPoses.add(p.getPosition());
        this.addObject(bmp);

        this.postToServer(p, bmp);
        mObjectPoseUpdated = true;
    }

    void postToServer(Pose p, Bitmap bmp) {
        // encode image
        String encodedImage = bitmapToString(bmp);

        AsyncHttpClient client = new AsyncHttpClient();

        RequestParams params = new RequestParams();
        params.put("x", p.getPosition().x);
        params.put("y", p.getPosition().y);
        params.put("z", p.getPosition().z);
        params.put("image", encodedImage);
        Log.d("ANDREW", encodedImage);

//        Bitmap b = stringToBitmap(encodedImage);
        Log.d("ANDREW", "ok");

        client.post(ENDPOINT, params, new AsyncHttpResponseHandler() {

            @Override
            public void onStart() {
                // called before request is started
                Log.d("ANDREW", "post started");
            }

            @Override
            public void onSuccess(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] responseBody) {
                Log.d("ANDREW", "post succeeded");
                fetchNew();
            }

            @Override
            public void onFailure(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] responseBody, Throwable error) {
                Log.d("ANDREW", "post failed");
            }

            @Override
            public void onRetry(int retryNo) {
                // called when request is retried
                Log.d("ANDREW", "post retry");
            }
        });
    }

    void fetchNew() {
        AsyncHttpClient client = new AsyncHttpClient();
        client.get(ENDPOINT, new AsyncHttpResponseHandler() {

            @Override
            public void onStart() {
                // called before request is started
                Log.d("ANDREW", "GET started");
            }

            @Override
            public void onSuccess(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] responseBody) {
                try {
                    String response = new String(responseBody, "UTF-8");
                    JSONArray jsonResponse = new JSONArray(response);
                    updateObjects(jsonResponse);
                } catch (Exception e){
                    //ignored

                    Log.d("ANDREW", "GET exception from parsing response: " + e.getLocalizedMessage());
                }
            }

            @Override
            public void onFailure(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] responseBody, Throwable error) {
                Log.d("ANDREW", "GET failed");
            }

            @Override
            public void onRetry(int retryNo) {
                // called when request is retried
                Log.d("ANDREW", "GET retry");
            }
        });
    }

    void updateObjects(JSONArray response) {

        for (int o = 0; o < mObjects.size(); o++) {
            getCurrentScene().removeChild(mObjects.get(o));
        }
        mObjects.clear();
        mObjectPoses.clear();

        Log.d("Andrew", response.length() + "");
        try {
            for (int i = 0; i < response.length(); i++) {
                String image = response.getJSONObject(i).getString("image");
                Double x = response.getJSONObject(i).getDouble("x");
                Double y = response.getJSONObject(i).getDouble("y");
                Double z = response.getJSONObject(i).getDouble("z");

                mObjectPoses.add(new Vector3(x, y, z));
                Bitmap b = stringToBitmap(image);
                addObject(b);
                mObjectPoseUpdated = true;
            }

            for (int j = 0; j < mObjects.size(); j++) {
                getCurrentScene().addChild(mObjects.get(j));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("ANDREW", "something happened: " + e.getLocalizedMessage() + ":" +  ":" + e.getMessage());
        }

    }

    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The device pose should match the pose of the device at the time the last rendered RGB
     * frame, which can be retrieved with this.getTimestamp();
     * <p/>
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData devicePose, DeviceExtrinsics extrinsics) {
        Pose cameraPose = ScenePoseCalculator.toOpenGlCameraPose(devicePose, extrinsics);
        getCurrentCamera().setRotation(cameraPose.getOrientation());
        getCurrentCamera().setPosition(cameraPose.getPosition());
    }

    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public int getTextureId() {
        return mTangoCameraTexture == null ? -1 : mTangoCameraTexture.getTextureId();
    }

    /**
     * We need to override this method to mark the camera for re-configuration (set proper
     * projection matrix) since it will be reset by Rajawali on surface changes.
     */
    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height) {
        super.onRenderSurfaceSizeChanged(gl, width, height);
        mSceneCameraConfigured = false;
    }

    public boolean isSceneCameraConfigured() {
        return mSceneCameraConfigured;
    }

    /**
     * Sets the projection matrix for the scen camera to match the parameters of the color camera,
     * provided by the {@code TangoCameraIntrinsics}.
     */
    public void setProjectionMatrix(TangoCameraIntrinsics intrinsics) {
        Matrix4 projectionMatrix = ScenePoseCalculator.calculateProjectionMatrix(
                intrinsics.width, intrinsics.height,
                intrinsics.fx, intrinsics.fy, intrinsics.cx, intrinsics.cy);
        getCurrentCamera().setProjectionMatrix(projectionMatrix);
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }

    public String bitmapToString(Bitmap in){
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        in.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        return Base64.encodeToString(bytes.toByteArray(),Base64.NO_PADDING);
    }
    public Bitmap stringToBitmap(String in){
        Log.w("stringToBitmap", in);
        byte[] decodedString = Base64.decode(in.substring(0, in.length() - 2), Base64.NO_PADDING);
        Bitmap b = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

        return b;
    }
}
