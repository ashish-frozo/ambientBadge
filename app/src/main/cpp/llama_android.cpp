#include <jni.h>
#include <android/log.h>
#include <string>
#include <memory>
#include <vector>
#include <thread>
#include <mutex>
#include <fstream>
#include <sstream>

#define TAG "LlamaAndroid"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Mock LLaMA context structure for now
// In real implementation, would use actual llama.cpp structures
struct MockLlamaContext {
    std::string modelPath;
    int contextLength;
    float temperature;
    float topP;
    bool isLoaded = false;
    std::mutex generateMutex;
};

// Global context storage
static std::mutex contextsMutex;
static std::vector<std::unique_ptr<MockLlamaContext>> contexts;

// Helper to get context by handle
static MockLlamaContext* getContext(jlong handle) {
    std::lock_guard<std::mutex> lock(contextsMutex);
    for (auto& ctx : contexts) {
        if (reinterpret_cast<jlong>(ctx.get()) == handle) {
            return ctx.get();
        }
    }
    return nullptr;
}

// Mock medical prompt responses for testing
static const std::vector<std::string> mockResponses = {
    R"({
        "soap": {
            "subjective": ["Patient complains of headache for 2 days", "No fever or nausea"],
            "objective": ["Temperature 98.6°F", "Blood pressure 120/80 mmHg"],
            "assessment": ["Tension headache", "Mild dehydration"],
            "plan": ["Pain management", "Increase fluid intake"],
            "confidence": 0.85
        },
        "prescription": {
            "medications": [
                {
                    "name": "Acetaminophen",
                    "dosage": "500mg",
                    "frequency": "twice daily",
                    "duration": "3 days",
                    "instructions": "Take with food",
                    "confidence": 0.9,
                    "isGeneric": true
                }
            ],
            "instructions": ["Rest and adequate hydration"],
            "followUp": "Follow up if symptoms persist beyond 3 days",
            "confidence": 0.8
        }
    })",
    
    R"({
        "soap": {
            "subjective": ["Cough and cold symptoms for 5 days", "Sore throat and nasal congestion"],
            "objective": ["Temperature 100.2°F", "Throat appears red"],
            "assessment": ["Upper respiratory tract infection", "Mild fever"],
            "plan": ["Symptomatic treatment", "Rest and fluids"],
            "confidence": 0.78
        },
        "prescription": {
            "medications": [
                {
                    "name": "Amoxicillin",
                    "dosage": "500mg",
                    "frequency": "three times daily",
                    "duration": "7 days",
                    "instructions": "Complete full course",
                    "confidence": 0.85,
                    "isGeneric": true
                },
                {
                    "name": "Paracetamol",
                    "dosage": "650mg",
                    "frequency": "every 6 hours",
                    "duration": "as needed",
                    "instructions": "For fever and pain",
                    "confidence": 0.92,
                    "isGeneric": true
                }
            ],
            "instructions": ["Complete antibiotic course", "Maintain adequate hydration"],
            "followUp": "Return if fever persists beyond 48 hours of treatment",
            "confidence": 0.82
        }
    })"
};

// Mock generation based on simple keyword analysis
static std::string generateMockResponse(const std::string& prompt) {
    std::string lowerPrompt = prompt;
    std::transform(lowerPrompt.begin(), lowerPrompt.end(), lowerPrompt.begin(), ::tolower);
    
    // Simple keyword matching for different medical scenarios
    if (lowerPrompt.find("headache") != std::string::npos || 
        lowerPrompt.find("head") != std::string::npos) {
        return mockResponses[0];
    } else if (lowerPrompt.find("cough") != std::string::npos || 
               lowerPrompt.find("cold") != std::string::npos ||
               lowerPrompt.find("fever") != std::string::npos) {
        return mockResponses[1];
    }
    
    // Default response for other cases
    return mockResponses[0];
}

// Validate model file exists and is readable
static bool validateModelFile(const std::string& modelPath) {
    std::ifstream file(modelPath, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Cannot open model file: %s", modelPath.c_str());
        return false;
    }
    
    auto size = file.tellg();
    file.close();
    
    if (size < 1024 * 1024) { // At least 1MB for a valid model
        LOGE("Model file too small: %ld bytes", size);
        return false;
    }
    
    LOGI("Model file validated: %ld bytes", size);
    return true;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_frozo_ambientscribe_ai_LLMService_nativeInitialize(
    JNIEnv *env,
    jobject /* this */,
    jstring jModelPath,
    jint contextLength,
    jfloat temperature,
    jfloat topP) {
    
    // Convert Java string to C++ string
    const char* modelPathCStr = env->GetStringUTFChars(jModelPath, nullptr);
    std::string modelPath(modelPathCStr);
    env->ReleaseStringUTFChars(jModelPath, modelPathCStr);
    
    LOGI("Initializing LLaMA model: %s", modelPath.c_str());
    LOGI("Context length: %d, temperature: %.2f, top_p: %.2f", 
         contextLength, temperature, topP);
    
    // Validate model file
    if (!validateModelFile(modelPath)) {
        LOGE("Model validation failed");
        return 0;
    }
    
    try {
        // Create and initialize context
        auto context = std::make_unique<MockLlamaContext>();
        context->modelPath = modelPath;
        context->contextLength = contextLength;
        context->temperature = temperature;
        context->topP = topP;
        
        // In real implementation, would load actual LLaMA model here
        // For now, just mark as loaded for testing
        context->isLoaded = true;
        
        jlong handle = reinterpret_cast<jlong>(context.get());
        
        // Store context globally
        {
            std::lock_guard<std::mutex> lock(contextsMutex);
            contexts.push_back(std::move(context));
        }
        
        LOGI("LLaMA context initialized successfully, handle: %ld", handle);
        return handle;
        
    } catch (const std::exception& e) {
        LOGE("Failed to initialize LLaMA context: %s", e.what());
        return 0;
    }
}

JNIEXPORT jstring JNICALL
Java_com_frozo_ambientscribe_ai_LLMService_nativeGenerate(
    JNIEnv *env,
    jobject /* this */,
    jlong handle,
    jstring jPrompt) {
    
    MockLlamaContext* context = getContext(handle);
    if (!context || !context->isLoaded) {
        LOGE("Invalid context or model not loaded");
        return nullptr;
    }
    
    // Convert prompt to C++ string
    const char* promptCStr = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(promptCStr);
    env->ReleaseStringUTFChars(jPrompt, promptCStr);
    
    LOGI("Generating response for prompt length: %zu", prompt.length());
    
    std::lock_guard<std::mutex> lock(context->generateMutex);
    
    try {
        // Simulate processing time (real LLM would take longer)
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        
        // Generate mock response based on prompt content
        std::string response = generateMockResponse(prompt);
        
        LOGI("Generated response length: %zu", response.length());
        
        // Return response as Java string
        return env->NewStringUTF(response.c_str());
        
    } catch (const std::exception& e) {
        LOGE("Generation failed: %s", e.what());
        return nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_frozo_ambientscribe_ai_LLMService_nativeCleanup(
    JNIEnv *env,
    jobject /* this */,
    jlong handle) {
    
    LOGI("Cleaning up LLaMA context: %ld", handle);
    
    std::lock_guard<std::mutex> lock(contextsMutex);
    
    // Find and remove the context
    auto it = std::find_if(contexts.begin(), contexts.end(),
        [handle](const std::unique_ptr<MockLlamaContext>& ctx) {
            return reinterpret_cast<jlong>(ctx.get()) == handle;
        });
    
    if (it != contexts.end()) {
        contexts.erase(it);
        LOGI("Context cleaned up successfully");
    } else {
        LOGE("Context not found for cleanup");
    }
}

} // extern "C"