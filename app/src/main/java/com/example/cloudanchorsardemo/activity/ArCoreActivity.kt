package com.example.cloudanchorsardemo.activity

import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Html
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.cloudanchorsardemo.R
import com.example.cloudanchorsardemo.database.FirebaseDatabaseManager
import com.example.cloudanchorsardemo.dialog.ResolveDialog
import com.example.cloudanchorsardemo.fragment.ArCoreFragment
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import kotlinx.android.synthetic.main.activity_arcore.*
import java.util.concurrent.CompletableFuture


class ArCoreActivity : AppCompatActivity() {

    // Declare a CloudAnchor and an AppAnchorState
    private var cloudAnchor: Anchor? = null
    private var bottleCloudAnchor: Anchor? = null
    private var textureCloudAnchor: Anchor? = null
    private var appAnchorState = AppAnchorState.NONE

    private var arCoreFragment: ArCoreFragment? = null
    private var firebaseDatabaseManager: FirebaseDatabaseManager? = null
    private var manualShortCode = 99;

    private enum class AppAnchorState {
        NONE,
        HOSTING,
        HOSTED,
        RESOLVING,
        RESOLVED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arcore)

        arCoreFragment = supportFragmentManager.findFragmentById(R.id.sceneform_fragment) as ArCoreFragment?
        arCoreFragment?.planeDiscoveryController?.hide()
        arCoreFragment?.arSceneView?.scene?.addOnUpdateListener {
            Scene.OnUpdateListener { p0 -> updateAnchorIfNecessary() }
        }
        arCoreFragment?.arSceneView?.scene?.addOnUpdateListener { p0 -> updateAnchorIfNecessary() }
        firebaseDatabaseManager = FirebaseDatabaseManager(this)

        initListeners()
    }

    private fun initListeners() {
        clear_button.setOnClickListener {
            setCloudAnchor(null, 99)
        }

        resolve_button.setOnClickListener(View.OnClickListener {
            ResolveDialog(
                this,
                object : ResolveDialog.PositiveButtonListener {
                    override fun onPositiveButtonClicked(dialogValue: String) {
                        resolveAnchor(dialogValue)
                    }
                },
                getString(R.string.resolve),
                View.VISIBLE,
                View.VISIBLE
            ).show()
        })


        arCoreFragment?.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, _: MotionEvent ->
            var viewGenerator = layoutInflater.inflate(R.layout.view_generator, null) as LinearLayout;
            AlertDialog.Builder(this)
                .setView(viewGenerator)
                .setPositiveButton("Create", DialogInterface.OnClickListener {dialog, which ->
                    Log.i("dhl", "\n\n\nARFragment was tapped.");
                    var siteName = viewGenerator.
                    renderObject(hitResult);
                })
                .create()
                .show();
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING || appAnchorState != AppAnchorState.NONE) {}
        }
    }

    fun resolveAnchor(dialogValue: String) {

        val shortCode = Integer.parseInt(dialogValue)

        firebaseDatabaseManager?.getCloudAnchorID(shortCode, object :
            FirebaseDatabaseManager.CloudAnchorIdListener {
            override fun onCloudAnchorIdAvailable(cloudAnchorId: String?) {

                val resolvedAnchor = arCoreFragment?.arSceneView?.session?.resolveCloudAnchor(cloudAnchorId)
                setCloudAnchor(resolvedAnchor, shortCode)
                showMessage("Now Resolving Anchor...")

                if(shortCode == 1) {
                    arCoreFragment?.let { placeObject(it, bottleCloudAnchor, shortCode) }
                } else if (shortCode == 2) {
                    arCoreFragment?.let { placeObject(it, textureCloudAnchor, shortCode) }
                }
                appAnchorState = AppAnchorState.RESOLVING
            }

        })

    }

    fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setCloudAnchor(newAnchor: Anchor?, manualShortCode: Int) {
        if (cloudAnchor != null) {
            cloudAnchor?.detach()
        }

        if (manualShortCode == 1) {
//            bottleCloudAnchor?.detach()
            bottleCloudAnchor = newAnchor
        } else if (manualShortCode == 2) {
//            textureCloudAnchor?.detach();
            textureCloudAnchor = newAnchor;
        }

        cloudAnchor = newAnchor
        appAnchorState = AppAnchorState.NONE
    }

    @Synchronized
    private fun updateAnchorIfNecessary() {
        if (appAnchorState != AppAnchorState.HOSTING && appAnchorState != AppAnchorState.RESOLVING) {
            return
        }
        val cloudState = cloudAnchor?.cloudAnchorState
        cloudState?.let { it ->
            if (appAnchorState == AppAnchorState.HOSTING) {
                if (it.isError) {
                    Toast.makeText(this, "Error hosting anchor.. $it", Toast.LENGTH_LONG).show()

                    appAnchorState = AppAnchorState.NONE
                } else if (it == Anchor.CloudAnchorState.SUCCESS) {
                    firebaseDatabaseManager?.nextShortCode(object :
                        FirebaseDatabaseManager.ShortCodeListener {
                        override fun onShortCodeAvailable(shortCode: Int?) {
                            if (shortCode == null) {
                                showMessage("Could not get shortCode")
                                return
                            }
                            cloudAnchor?.let {
                                firebaseDatabaseManager?.storeUsingShortCode(
                                    manualShortCode,
                                    it.cloudAnchorId
                                )
                            }
                            showMessageWitAnchorId("Anchor hosted with: " + manualShortCode)
                        }

                    })
                    appAnchorState = AppAnchorState.HOSTED
                }
            } else if (appAnchorState == AppAnchorState.RESOLVING) {
                if (it.isError) {
                    Toast.makeText(this, "Error hosting anchor.. $it", Toast.LENGTH_LONG).show()

                    appAnchorState = AppAnchorState.NONE
                } else if (it == Anchor.CloudAnchorState.SUCCESS) {
                    Toast.makeText(this, "Anchor resolved successfully", Toast.LENGTH_LONG).show()

                    appAnchorState = AppAnchorState.RESOLVED
                }
            }
        }

    }

    private fun showMessageWitAnchorId(s: String) {
        ResolveDialog(
            this,
            object : ResolveDialog.PositiveButtonListener {
                override fun onPositiveButtonClicked(dialogValue: String) {
                    resolveAnchor(dialogValue)
                }
            },
            s,
            View.GONE,
            View.GONE
        ).show()
    }

    private fun placeObject(fragment: ArFragment, anchor: Anchor?, stageNumber: Int) {
        var view: View?;
        if(stageNumber == 1) {
           view = layoutInflater.inflate(R.layout.bottle_station, null);
        } else if (stageNumber == 2){
           view = layoutInflater.inflate(R.layout.texture_wall, null);
        } else {
            view = layoutInflater.inflate(R.layout.bottle_station, null);
        }
        ViewRenderable.builder()
            .setView(fragment.context, view)
            .build()
            .thenAccept { renderable -> addNodeToScene(fragment, anchor, renderable) }
            .exceptionally { throwable ->
                val builder = android.app.AlertDialog.Builder(this)
                builder.setMessage(throwable.message)
                    .setTitle("Error!")
                val dialog = builder.create()
                dialog.show()
                null
            }
    }

    private fun addNodeToScene(fragment: ArFragment, anchor: Anchor?, renderable: Renderable) {
        val anchorNode = AnchorNode(anchor)
        val node = TransformableNode(fragment.transformationSystem)
        node.setParent(anchorNode)
        node.renderable = renderable;
        fragment.arSceneView.scene.addChild(anchorNode)
        node.select()
    }

    private fun renderObject(hitResult: HitResult) {
        var userInputView = layoutInflater.inflate(R.layout.user_input, null) as EditText;
        AlertDialog.Builder(this)
            .setView(userInputView)
            .setPositiveButton("Add", DialogInterface.OnClickListener { dialog, which ->
                manualShortCode = Integer.parseInt(userInputView.text.toString());
                val newAnchor = arCoreFragment?.arSceneView?.session?.hostCloudAnchor(hitResult.createAnchor())
                setCloudAnchor(newAnchor, manualShortCode)
                appAnchorState = AppAnchorState.HOSTING
                Toast.makeText(this, "Now hosting anchor...", Toast.LENGTH_LONG).show()
                if(manualShortCode == 1) {
                    arCoreFragment?.let { placeObject(it, bottleCloudAnchor, manualShortCode) }
                } else if (manualShortCode == 2) {
                    arCoreFragment?.let { placeObject(it, textureCloudAnchor, manualShortCode) }
                }
            })
            .setNegativeButton("Cancel", null)
            .create()
            .show();
    }
}
