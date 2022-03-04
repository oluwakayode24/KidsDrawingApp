package khay.kotlinlearning.kidsdrawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.Window
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null
    private var mImageBtnCurrentPaint: ImageButton? = null //for ImageBtn pallet
    var customProgressDialog : Dialog? = null

    //creating a Launcher and passing Intent
    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            //checking for result code is ok and make sure its not empty then assigning it to imageBackground
            if(result.resultCode == RESULT_OK && result.data!= null){
                val imageBackground: ImageView = findViewById(R.id.iv_background)

                //URI is location on device
                imageBackground.setImageURI(result.data?.data)
            }
        }

        //Setting App permission request
    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value

                if(isGranted){
                    Toast.makeText(this@MainActivity,
                        "Permission granted now you can read the storage files.",
                    Toast.LENGTH_LONG).show()
                    //code for permission adding image
                    val pickIntent = Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent) //launching gallery
                }else{
                    if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                        Toast.makeText(this@MainActivity,
                            "Permission  not granted.",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView =findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.toFloat())


        //finding pallet ImageButton in LinearLayout and setting the drawable as ImageButton
        var llPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)
        mImageBtnCurrentPaint = llPaintColors[6] as ImageButton
        mImageBtnCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.selected_pallet)
        )

        //putting BrushDialogPickerDialog into ImageButton
        val ibBrush: ImageButton = findViewById(R.id.ib_brush)
        ibBrush.setOnClickListener {
            showBrushSizePickerDialog()
        }

        //setting undo button
        val ibUndo: ImageButton = findViewById(R.id.ib_undo)
        ibUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }

        //setting save button
        val ibSave: ImageButton = findViewById(R.id.ib_save)
        ibSave.setOnClickListener {
            if(isReadStorageAllowed()){
                showProgressDialog() //showing progress Dialog
                lifecycleScope.launch {
                    val flDrawingView: FrameLayout = findViewById(R.id.fl_drawing_view_container)
                    val myBitmap: Bitmap = getBitmapFromView(flDrawingView)
                    saveBitmapFile(myBitmap)
                }
            }
        }


        //Setting the gallery ImageButton
        val ibGallery : ImageButton = findViewById(R.id.ib_gallery)
        ibGallery.setOnClickListener {
            requestStoragePermission()
        }

    }

    private fun showBrushSizePickerDialog(){
        var brushDialog = Dialog(this)
        brushDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")


        //setting the ImageButton Dialog box
        val smallBtn : ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }

        val mediumBtn : ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }

        val largeBtn : ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    //function for onClick event
    fun paintClicked(view: View){
        if(view !== mImageBtnCurrentPaint){
            val imageButton = view as ImageButton //use current view as ImageButton
            val colorTag = imageButton.tag.toString() //reading imageButton tag from the imageButton xml file
            drawingView?.setColor(colorTag) //use the colorTag as the newColor

            //setting the pressed button color to selected_pallet
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.selected_pallet)
            )

            //setting the unPressed button color to pallet_normal
            mImageBtnCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_normal)
            )

            mImageBtnCurrentPaint = view //hold the current button clicked on
        }
    }

    //function to handle Permission request
    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(this,
            Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationaleDialog("Kids Drawing App",
                "Kids Drawing APP needs to Access Your External Storage otherwise " +
                        "you can't Access Background Image")
        } else{
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }
    //checking external storage permission
    private fun isReadStorageAllowed(): Boolean{
        val result = ContextCompat.checkSelfPermission(this,
        Manifest.permission.READ_EXTERNAL_STORAGE)

        return result == PackageManager.PERMISSION_GRANTED
    }

    //function for dialog
    private fun showRationaleDialog(title: String, message: String){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title).setMessage(message).setPositiveButton("Cancel"){
            dialog, _ ->
            dialog.dismiss()
        }
        builder.create().show()
    }

    //function for getting Bitmap to save image
    private fun getBitmapFromView(view: View) : Bitmap{
        val returnedBitmap = Bitmap.createBitmap(view.width,
            view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable != null) {
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    //storing Bitmap on device using Coroutine
    private suspend fun saveBitmapFile(mBitmap: Bitmap?) : String{
        var result = ""
        withContext(Dispatchers.IO){
            if (mBitmap != null){
                try{
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90,bytes)// image quality

                    //creating file and storing location
                    val file = File(externalCacheDir?.absoluteFile.toString() +
                            File.separator.toString() + "KidsDrawingApp_" +
                            System.currentTimeMillis()/1000 + ".png"
                    )

                    val fileOutput = FileOutputStream(file)
                    fileOutput.write(bytes.toByteArray())
                    fileOutput.close()

                    result = file.absolutePath

                    //running the UI code task
                    runOnUiThread {
                        if(result.isNotEmpty()){
                            cancelProgressDialog()// hiding/cancel the progress dialog
                            Toast.makeText(this@MainActivity,
                                "File saved successfully : $result", Toast.LENGTH_LONG
                            ).show()
                            shareImageFile(result)//giving image sharing option
                        }else{
                            Toast.makeText(this@MainActivity,
                                "Something went wrong while saving the file: ", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                catch (e: Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    //showing saving progress Dialog via a Custom Dialog
    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@MainActivity)

        //Set screen content from Layout Resource, resource will be inflated adding all top-levels views to the screen
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)

        //start the dialog and display it on screen
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        if(customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImageFile(result: String){

        MediaScannerConnection.scanFile(this,arrayOf(result), null){
            path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share"))

        }
    }
}